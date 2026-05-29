package com.example.lambda;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AWS Textract service for Lambda environment.
 * Extracts table data from PDFs stored in S3.
 */
public class LambdaTextractService {

    private final TextractClient textract;

    public LambdaTextractService(String region) {
        this.textract = TextractClient.builder()
                .region(Region.of(region.trim()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Run async document analysis with TABLES feature.
     * Required for multi-page PDFs. Polls until completion.
     */
    public List<List<List<String>>> analyzeTables(String bucket, String key) throws InterruptedException {
        // Start async job
        StartDocumentAnalysisResponse start = textract.startDocumentAnalysis(
            StartDocumentAnalysisRequest.builder()
                .documentLocation(DocumentLocation.builder()
                    .s3Object(S3Object.builder()
                        .bucket(bucket)
                        .name(key)
                        .build())
                    .build())
                .featureTypes(FeatureType.TABLES)
                .build()
        );

        String jobId = start.jobId();
        System.out.println("Started Textract job: " + jobId);

        // Poll for completion (optimized: check every 1 second instead of 2)
        List<Block> allBlocks = new ArrayList<>();
        String nextToken = null;

        while (true) {
            GetDocumentAnalysisResponse response = textract.getDocumentAnalysis(
                GetDocumentAnalysisRequest.builder()
                    .jobId(jobId)
                    .nextToken(nextToken)
                    .build()
            );

            JobStatus status = response.jobStatus();

            if (status == JobStatus.SUCCEEDED) {
                allBlocks.addAll(response.blocks());
                nextToken = response.nextToken();

                if (nextToken == null) {
                    break; // All pages processed
                }
            } else if (status == JobStatus.FAILED || status == JobStatus.PARTIAL_SUCCESS) {
                throw new RuntimeException("Textract job failed: " + status + " - " + response.statusMessage());
            } else {
                // Still processing, wait before polling again (1 second for faster response)
                Thread.sleep(Duration.ofSeconds(1).toMillis());
            }
        }

        System.out.println("✅ Textract completed, processing " + allBlocks.size() + " blocks");
        return blocksToTables(allBlocks);
    }

    /**
     * Convert Textract blocks to table structure: List of tables -> rows -> cells
     */
    private static List<List<List<String>>> blocksToTables(List<Block> blocks) {
        Map<String, Block> blockMap = blocks.stream()
                .collect(Collectors.toMap(Block::id, b -> b));

        List<List<List<String>>> tables = new ArrayList<>();

        for (Block block : blocks) {
            if (block.blockType() != BlockType.TABLE) continue;

            // Build grid: row -> column -> text
            Map<Integer, Map<Integer, String>> grid = new TreeMap<>();

            List<Relationship> relationships = block.relationships() == null
                    ? Collections.emptyList()
                    : block.relationships();

            for (Relationship rel : relationships) {
                if (!"CHILD".equals(rel.typeAsString())) continue;

                for (String id : rel.ids()) {
                    Block cell = blockMap.get(id);
                    if (cell == null || cell.blockType() != BlockType.CELL) continue;

                    int row = cell.rowIndex() == null ? 0 : cell.rowIndex();
                    int col = cell.columnIndex() == null ? 0 : cell.columnIndex();

                    grid.computeIfAbsent(row, k -> new TreeMap<>())
                        .put(col, extractCellText(cell, blockMap));
                }
            }

            // Convert grid to list of rows
            List<List<String>> tableRows = new ArrayList<>();
            for (Map<Integer, String> rowCells : grid.values()) {
                tableRows.add(new ArrayList<>(rowCells.values()));
            }

            tables.add(tableRows);
        }

        return tables;
    }

    /**
     * Extract text from a cell by concatenating child WORD blocks.
     */
    private static String extractCellText(Block cell, Map<String, Block> blockMap) {
        List<String> words = new ArrayList<>();

        List<Relationship> relationships = cell.relationships() == null
                ? Collections.emptyList()
                : cell.relationships();

        for (Relationship rel : relationships) {
            if (!"CHILD".equals(rel.typeAsString())) continue;

            for (String id : rel.ids()) {
                Block child = blockMap.get(id);
                if (child == null) continue;

                if (child.blockType() == BlockType.WORD) {
                    words.add(child.text());
                } else if (child.blockType() == BlockType.SELECTION_ELEMENT
                        && child.selectionStatus() == SelectionStatus.SELECTED) {
                    words.add("☑");
                }
            }
        }

        return String.join(" ", words).trim();
    }

    /**
     * Map extracted tables to transaction records.
     * Normalizes common bank statement headers to standard fields.
     */
    public static List<Map<String, String>> mapTransactions(List<List<List<String>>> tables) {
        List<Map<String, String>> transactions = new ArrayList<>();

        if (tables == null || tables.isEmpty()) {
            return transactions;
        }

        for (List<List<String>> table : tables) {
            if (table == null || table.isEmpty()) continue;

            // First row is header
            List<String> headerRow = table.get(0).stream()
                    .map(String::trim)
                    .collect(Collectors.toList());

            List<String> normalizedKeys = normalizeHeader(headerRow);

            // Remaining rows are data
            for (int i = 1; i < table.size(); i++) {
                List<String> row = table.get(i);

                // Skip blank rows
                boolean allBlank = row.stream()
                        .allMatch(s -> s == null || s.trim().isEmpty());
                if (allBlank) continue;

                Map<String, String> transaction = new LinkedHashMap<>();
                for (int col = 0; col < Math.min(normalizedKeys.size(), row.size()); col++) {
                    String value = row.get(col) == null ? "" : row.get(col).trim();
                    transaction.put(normalizedKeys.get(col), value);
                }

                transactions.add(transaction);
            }
        }

        return transactions;
    }

    /**
     * Normalize bank statement headers to standard field names.
     */
    private static List<String> normalizeHeader(List<String> header) {
        return header.stream().map(h -> {
            String s = (h == null ? "" : h).trim().toLowerCase();

            if (s.contains("date")) return "date";
            if (s.contains("descr") || s.contains("narrative") || s.contains("details") || s.contains("description"))
                return "description";
            if (s.contains("withdrawn") || s.contains("debit") || s.contains("money out"))
                return "amount";
            if (s.contains("credit") || s.contains("lodg") || s.contains("money in"))
                return "credit";
            if (s.contains("amount"))
                return "amount";
            if (s.contains("balance") || s.contains("bal"))
                return "balance";

            // Fallback: use original header with spaces replaced
            return h == null ? "col" : h.replaceAll("\\s+", "_");
        }).collect(Collectors.toList());
    }
}
