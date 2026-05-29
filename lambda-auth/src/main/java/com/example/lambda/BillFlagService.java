package com.example.lambda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic bill detection and flagging service.
 * Detects all Irish household bill types by provider name patterns,
 * then flags bills that exceed age/type-based thresholds.
 */
public class BillFlagService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ── Thresholds (monthly amount in EUR) ──
    // Mobile Phone: age-based
    private static final double MOBILE_THRESHOLD_UNDER_21 = 50.0;
    private static final double MOBILE_THRESHOLD_OVER_65  = 30.0;
    private static final double MOBILE_THRESHOLD_GENERAL  = 70.0;

    // Other bill type thresholds
    private static final double ELECTRICITY_THRESHOLD     = 200.0;
    private static final double GAS_THRESHOLD             = 180.0;
    private static final double BROADBAND_THRESHOLD       = 70.0;
    private static final double HEALTH_INSURANCE_THRESHOLD = 250.0;
    private static final double CAR_INSURANCE_THRESHOLD   = 90.0;
    private static final double HOME_INSURANCE_THRESHOLD  = 60.0;
    private static final double SUBSCRIPTIONS_THRESHOLD   = 50.0;
    // No threshold for: Mortgage, Public Transport, TV License, Water/Waste, Property Tax

    // ── Provider detection patterns per bill type ──

    private static final Map<String, List<Pattern>> BILL_TYPE_PATTERNS = new LinkedHashMap<>();

    static {
        BILL_TYPE_PATTERNS.put("Mobile Phone", Arrays.asList(
                Pattern.compile("vodafone", Pattern.CASE_INSENSITIVE),
                Pattern.compile("three\\s*ireland|three\\.ie|three\\s*mobile|3\\s*mobile", Pattern.CASE_INSENSITIVE),
                Pattern.compile("eir\\s*mobile|eirmobile|eir\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("tesco\\s*mobile", Pattern.CASE_INSENSITIVE),
                Pattern.compile("lycamobile|lyca\\s*mobile", Pattern.CASE_INSENSITIVE),
                Pattern.compile("virgin\\s*mobile", Pattern.CASE_INSENSITIVE),
                Pattern.compile("giffgaff", Pattern.CASE_INSENSITIVE),
                Pattern.compile("48\\s*mobile|48\\.ie", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Electricity", Arrays.asList(
                Pattern.compile("electric\\s*ireland", Pattern.CASE_INSENSITIVE),
                Pattern.compile("sse\\s*airtricity", Pattern.CASE_INSENSITIVE),
                Pattern.compile("energia", Pattern.CASE_INSENSITIVE),
                Pattern.compile("bord\\s*g[aá]is\\s*energy|bord\\s*gais", Pattern.CASE_INSENSITIVE),
                Pattern.compile("flogas", Pattern.CASE_INSENSITIVE),
                Pattern.compile("pinergy", Pattern.CASE_INSENSITIVE),
                Pattern.compile("prepay\\s*power|prepaypower", Pattern.CASE_INSENSITIVE),
                Pattern.compile("waterpower", Pattern.CASE_INSENSITIVE),
                Pattern.compile("yuno\\s*energy", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Gas", Arrays.asList(
                Pattern.compile("bord\\s*g[aá]is(?!.*energy)|bord\\s*gais(?!.*energy)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("gas\\s*networks|gas\\s*ireland", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Broadband", Arrays.asList(
                Pattern.compile("eir(?!\\s*mobile|mobile)\\b|eir\\s*broadband|eir\\s*fibre", Pattern.CASE_INSENSITIVE),
                Pattern.compile("virgin\\s*media", Pattern.CASE_INSENSITIVE),
                Pattern.compile("sky\\s*ireland|sky\\s*broadband", Pattern.CASE_INSENSITIVE),
                Pattern.compile("vodafone\\s*broadband|vodafone.*fibre", Pattern.CASE_INSENSITIVE),
                Pattern.compile("digiweb", Pattern.CASE_INSENSITIVE),
                Pattern.compile("pure\\s*telecom", Pattern.CASE_INSENSITIVE),
                Pattern.compile("imagine\\s*broadband|imagine\\s*communications", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Health Insurance", Arrays.asList(
                Pattern.compile("vhi\\s*healthcare|vhi\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("irish\\s*life\\s*health", Pattern.CASE_INSENSITIVE),
                Pattern.compile("laya\\s*healthcare|laya\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("level\\s*health", Pattern.CASE_INSENSITIVE),
                Pattern.compile("hsf\\s*health", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Car Insurance", Arrays.asList(
                Pattern.compile("fbd\\s*insurance|fbd\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("aviva\\s*(?:car|motor|insurance)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("axa\\s*(?:car|motor|insurance)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("123\\.ie|123\\s*insurance", Pattern.CASE_INSENSITIVE),
                Pattern.compile("irish\\s*life\\s*insurance", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Home Insurance", Arrays.asList(
                Pattern.compile("fbd\\s*home|fbd\\s*house", Pattern.CASE_INSENSITIVE),
                Pattern.compile("aviva\\s*home|aviva\\s*house", Pattern.CASE_INSENSITIVE),
                Pattern.compile("axa\\s*home|axa\\s*house", Pattern.CASE_INSENSITIVE),
                Pattern.compile("123\\.ie\\s*home|123\\s*home", Pattern.CASE_INSENSITIVE),
                Pattern.compile("zurich\\s*home|zurich\\s*house", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Subscriptions", Arrays.asList(
                Pattern.compile("netflix", Pattern.CASE_INSENSITIVE),
                Pattern.compile("disney\\s*\\+|disney\\s*plus|disneyplus", Pattern.CASE_INSENSITIVE),
                Pattern.compile("amazon\\s*prime|prime\\s*video", Pattern.CASE_INSENSITIVE),
                Pattern.compile("spotify", Pattern.CASE_INSENSITIVE),
                Pattern.compile("apple\\.com/bill|apple\\s*icloud|icloud", Pattern.CASE_INSENSITIVE),
                Pattern.compile("google\\s*one|google\\s*storage", Pattern.CASE_INSENSITIVE),
                Pattern.compile("youtube\\s*premium", Pattern.CASE_INSENSITIVE),
                Pattern.compile("now\\s*tv|nowtv", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Mortgage", Arrays.asList(
                Pattern.compile("aib\\s*mortgage|aib\\s*home\\s*loan", Pattern.CASE_INSENSITIVE),
                Pattern.compile("boi\\s*mortgage|bank\\s*of\\s*ireland\\s*mortgage", Pattern.CASE_INSENSITIVE),
                Pattern.compile("ptsb\\s*mortgage|permanent\\s*tsb\\s*mortgage", Pattern.CASE_INSENSITIVE),
                Pattern.compile("haven\\s*mortgage|haven\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("ebs\\s*mortgage|ebs\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("avant\\s*money", Pattern.CASE_INSENSITIVE),
                Pattern.compile("ics\\s*mortgage|ics\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("moco|bawag", Pattern.CASE_INSENSITIVE),
                Pattern.compile("nua\\s*mortgage|nua\\b", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Public Transport", Arrays.asList(
                Pattern.compile("leap\\s*card|leap\\s*top|leapcard", Pattern.CASE_INSENSITIVE),
                Pattern.compile("transport\\s*for\\s*ireland|tfi\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("dublin\\s*bus|bus\\s*eireann|irish\\s*rail|luas|dart", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("TV Licence", Arrays.asList(
                Pattern.compile("tv\\s*licen[cs]e|television\\s*licen[cs]e", Pattern.CASE_INSENSITIVE),
                Pattern.compile("rte\\s*licen[cs]e|rte\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("an\\s*post\\s*tv", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Water/Waste", Arrays.asList(
                Pattern.compile("uisce\\s*eireann|irish\\s*water", Pattern.CASE_INSENSITIVE),
                Pattern.compile("panda\\s*waste|panda\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("greenstar|green\\s*star", Pattern.CASE_INSENSITIVE),
                Pattern.compile("citybin|city\\s*bin", Pattern.CASE_INSENSITIVE),
                Pattern.compile("oxigen|country\\s*clean", Pattern.CASE_INSENSITIVE)
        ));

        BILL_TYPE_PATTERNS.put("Property Tax", Arrays.asList(
                Pattern.compile("revenue\\s*lpt|local\\s*property\\s*tax|lpt\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("revenue\\.ie|revenue\\s*commissioners", Pattern.CASE_INSENSITIVE)
        ));
    }

    // ── Bill types that share providers with energy (detect both Electricity and Gas) ──
    private static final List<Pattern> ENERGY_DUAL_PATTERNS = Arrays.asList(
            Pattern.compile("electric\\s*ireland", Pattern.CASE_INSENSITIVE),
            Pattern.compile("sse\\s*airtricity", Pattern.CASE_INSENSITIVE),
            Pattern.compile("energia", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bord\\s*g[aá]is|bord\\s*gais", Pattern.CASE_INSENSITIVE),
            Pattern.compile("flogas", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pinergy", Pattern.CASE_INSENSITIVE),
            Pattern.compile("yuno\\s*energy", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Detect all bills from transactions. Returns one DetectedBill per matching
     * transaction (not summed). This is fully deterministic — same input always
     * produces the same output.
     */
    public static List<DetectedBill> detectAllBills(String transactionsJson) {
        List<DetectedBill> detected = new ArrayList<>();

        try {
            JsonNode transactions = objectMapper.readTree(transactionsJson);

            for (JsonNode txn : transactions) {
                String description = getFieldIgnoreCase(txn, "description");
                String withdrawnStr = getFieldIgnoreCase(txn, "amount");
                if (withdrawnStr.isEmpty() || withdrawnStr.isBlank()) {
                    withdrawnStr = getFieldIgnoreCase(txn, "withdrawn");
                }
                if (withdrawnStr.isEmpty() || withdrawnStr.isBlank()) {
                    withdrawnStr = getFieldIgnoreCase(txn, "debit");
                }

                if (withdrawnStr.isEmpty() || withdrawnStr.isBlank()) {
                    continue;
                }

                double amount;
                try {
                    amount = Double.parseDouble(withdrawnStr.replace(",", ""));
                } catch (NumberFormatException e) {
                    continue;
                }

                // Check each bill type
                for (Map.Entry<String, List<Pattern>> entry : BILL_TYPE_PATTERNS.entrySet()) {
                    String billType = entry.getKey();
                    String provider = matchProvider(description, entry.getValue());
                    if (provider != null) {
                        detected.add(new DetectedBill(billType, provider, amount));
                        break; // Only match first bill type per transaction
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in bill detection: " + e.getMessage());
        }

        return detected;
    }

    /**
     * Scan transactions for all bill types and flag any that exceed
     * the threshold for the user's age bracket / bill type.
     *
     * @param transactionsJson JSON array of transactions (Textract output)
     * @param dateOfBirth      user's date of birth (yyyy-MM-dd or similar)
     * @return list of flagged bills (empty if nothing to flag)
     */
    public static List<BillFlag> flagBills(String transactionsJson, String dateOfBirth) {
        List<BillFlag> flags = new ArrayList<>();

        int age = calculateAge(dateOfBirth);
        if (age < 0) {
            System.err.println("Could not calculate age from dateOfBirth: " + dateOfBirth);
            // Still detect bills but skip age-based mobile thresholds
        }

        List<DetectedBill> detectedBills = detectAllBills(transactionsJson);

        // Only flag once per provider — use the first matching transaction amount
        Set<String> flaggedProviders = new HashSet<>();
        for (DetectedBill bill : detectedBills) {
            String key = bill.billType + "|" + bill.providerName;
            if (flaggedProviders.contains(key)) {
                continue;
            }
            BillFlag flag = checkThreshold(bill, age);
            if (flag != null) {
                flags.add(flag);
                flaggedProviders.add(key);
                System.out.println("FLAGGED: " + flag.reason);
            }
        }

        return flags;
    }

    // ── Threshold checks per bill type ──

    private static BillFlag checkThreshold(DetectedBill bill, int age) {
        switch (bill.billType) {
            case "Mobile Phone":
                return checkMobileThreshold(bill.providerName, bill.amount, age);
            case "Electricity":
                return checkSimpleThreshold(bill, ELECTRICITY_THRESHOLD,
                        "The average Irish electricity bill is around EUR 151/month. You could save by comparing rates on bonkers.ie or switching to a discount plan.");
            case "Gas":
                return checkSimpleThreshold(bill, GAS_THRESHOLD,
                        "The average Irish gas bill is around EUR 131/month. Consider comparing gas providers or improving home insulation to reduce costs.");
            case "Broadband":
                return checkSimpleThreshold(bill, BROADBAND_THRESHOLD,
                        "Most Irish broadband plans range from EUR 35-60/month. Check if a cheaper package or bundle deal is available from your provider.");
            case "Health Insurance":
                return checkSimpleThreshold(bill, HEALTH_INSURANCE_THRESHOLD,
                        "The average health insurance premium in Ireland is around EUR 157/month per adult. Use the HIA comparison tool to find a plan with similar cover for less.");
            case "Car Insurance":
                return checkSimpleThreshold(bill, CAR_INSURANCE_THRESHOLD,
                        "Average Irish car insurance is EUR 52-66/month. Shop around at renewal time — switching provider often saves 10-20%.");
            case "Home Insurance":
                return checkSimpleThreshold(bill, HOME_INSURANCE_THRESHOLD,
                        "Average Irish home insurance is around EUR 36/month. Compare quotes at renewal — loyalty rarely pays with insurance.");
            case "Subscriptions":
                return checkSimpleThreshold(bill, SUBSCRIPTIONS_THRESHOLD,
                        "Your streaming/subscription spend is above average. Consider rotating subscriptions rather than paying for all simultaneously.");
            default:
                // Mortgage, Public Transport, TV Licence, Water/Waste, Property Tax — no threshold
                return null;
        }
    }

    private static BillFlag checkMobileThreshold(String provider, double amount, int age) {
        if (age < 0) {
            // Can't determine age — use general threshold
            if (amount > MOBILE_THRESHOLD_GENERAL) {
                return new BillFlag("Mobile Phone", provider, amount,
                        "Paying over EUR " + (int) MOBILE_THRESHOLD_GENERAL + "/month for mobile phone",
                        "You're paying EUR " + String.format("%.2f", amount) + " to " + provider
                                + ". Most SIM-only plans with unlimited data are available for EUR 20-40/month. It may be worth reviewing your plan.");
            }
            return null;
        }

        if (age < 21 && amount > MOBILE_THRESHOLD_UNDER_21) {
            return new BillFlag("Mobile Phone", provider, amount,
                    "Under 21 and paying over EUR " + (int) MOBILE_THRESHOLD_UNDER_21 + "/month for mobile phone",
                    "You're paying EUR " + String.format("%.2f", amount) + " to " + provider
                            + ". Most providers offer SIM-only or student plans between EUR 10-25/month "
                            + "that include plenty of data. It could be worth checking if a cheaper deal is available.");
        }

        if (age >= 65 && amount > MOBILE_THRESHOLD_OVER_65) {
            return new BillFlag("Mobile Phone", provider, amount,
                    "Over 65 and paying over EUR " + (int) MOBILE_THRESHOLD_OVER_65 + "/month for mobile phone",
                    "You're paying EUR " + String.format("%.2f", amount) + " to " + provider
                            + ". Many providers offer senior or low-usage plans from EUR 8-15/month. "
                            + "If you mainly use your phone for calls and texts, you could save significantly.");
        }

        if (age >= 21 && age < 65 && amount > MOBILE_THRESHOLD_GENERAL) {
            return new BillFlag("Mobile Phone", provider, amount,
                    "Paying over EUR " + (int) MOBILE_THRESHOLD_GENERAL + "/month for mobile phone",
                    "You're paying EUR " + String.format("%.2f", amount) + " to " + provider
                            + ". Unless you're on a device contract, most SIM-only plans with unlimited data "
                            + "are available for EUR 20-40/month. It may be worth reviewing your plan.");
        }

        return null;
    }

    private static BillFlag checkSimpleThreshold(DetectedBill bill, double threshold, String adviceTemplate) {
        if (bill.amount > threshold) {
            return new BillFlag(
                    bill.billType,
                    bill.providerName,
                    bill.amount,
                    bill.billType + " spend of EUR " + String.format("%.2f", bill.amount)
                            + " exceeds typical threshold of EUR " + (int) threshold + "/month",
                    "You're paying EUR " + String.format("%.2f", bill.amount) + " to " + bill.providerName
                            + " for " + bill.billType.toLowerCase() + ". " + adviceTemplate
            );
        }
        return null;
    }

    // ── Helpers ──

    private static String getFieldIgnoreCase(JsonNode node, String fieldName) {
        // Try exact match first, then case-insensitive scan
        if (node.has(fieldName)) return node.get(fieldName).asText("");
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.equalsIgnoreCase(fieldName)) {
                return node.get(name).asText("");
            }
        }
        return "";
    }

    private static String matchProvider(String description, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(description);
            if (matcher.find()) {
                String match = matcher.group();
                // Title-case the provider name
                return Arrays.stream(match.split("\\s+"))
                        .map(w -> w.isEmpty() ? w : w.substring(0, 1).toUpperCase() + w.substring(1).toLowerCase())
                        .reduce((a, b) -> a + " " + b)
                        .orElse(match);
            }
        }
        return null;
    }

    /**
     * Calculate age from date-of-birth string.
     * Supports yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy formats.
     */
    static int calculateAge(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.isEmpty()) return -1;

        List<DateTimeFormatter> formats = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy")
        );

        for (DateTimeFormatter fmt : formats) {
            try {
                LocalDate dob = LocalDate.parse(dateOfBirth, fmt);
                return Period.between(dob, LocalDate.now()).getYears();
            } catch (DateTimeParseException ignored) {
            }
        }

        return -1;
    }

    // ── Result models ──

    /**
     * A bill detected from transaction data. Deterministic — same input always
     * produces the same detected bills.
     */
    public static class DetectedBill {
        public String billType;
        public String providerName;
        public double amount;

        public DetectedBill(String billType, String providerName, double amount) {
            this.billType = billType;
            this.providerName = providerName;
            this.amount = amount;
        }
    }

    /**
     * A detected bill that exceeded its threshold — includes reason and advice.
     */
    public static class BillFlag {
        public String billType;
        public String providerName;
        public double amount;
        public String reason;
        public String advice;
        public String ctaPrompt;
        public String ctaYes;
        public String ctaNo;

        public BillFlag(String billType, String providerName, double amount, String reason, String advice) {
            this.billType = billType;
            this.providerName = providerName;
            this.amount = amount;
            this.reason = reason;
            this.advice = advice;
            this.ctaPrompt = buildCtaPrompt(billType);
            this.ctaYes = "Yes, show me options";
            this.ctaNo = "Not right now";
        }

        private static String buildCtaPrompt(String billType) {
            switch (billType) {
                case "Mobile Phone":
                    return "We can help you find a better mobile plan. Interested?";
                case "Electricity":
                    return "We can check if there's a cheaper electricity rate for you. Want to see?";
                case "Gas":
                    return "We can look into a better gas deal for you. Want to see?";
                case "Broadband":
                    return "There might be a better broadband deal out there. Want us to look?";
                case "Health Insurance":
                    return "You could get the same cover for less. Want us to check?";
                case "Car Insurance":
                    return "Switching car insurer often saves 10-20%. Want us to compare?";
                case "Home Insurance":
                    return "A quick comparison could save you here. Want us to check?";
                case "Subscriptions":
                    return "A few small changes could free up some cash. Want some tips?";
                default:
                    return "We might be able to help you save here. Interested?";
            }
        }
    }
}
