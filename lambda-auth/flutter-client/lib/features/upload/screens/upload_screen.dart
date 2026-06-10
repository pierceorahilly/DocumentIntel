import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_pdfview/flutter_pdfview.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:path_provider/path_provider.dart';
import '../../../core/theme/app_colors.dart';
import '../../auth/providers/auth_provider.dart';
import '../providers/upload_provider.dart';

class UploadScreen extends ConsumerWidget {
  const UploadScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final uploadState = ref.watch(uploadProvider);
    final isIOS = Platform.isIOS;

    // Navigate to results when upload completes
    ref.listen<UploadState>(uploadProvider, (prev, next) {
      if (next.result != null && prev?.result == null) {
        context.go('/results');
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: const Text('Trustal'),
        actions: [
          if (isIOS)
            CupertinoButton(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: const Text('Logout',
                  style: TextStyle(color: Colors.white)),
              onPressed: () {
                ref.read(authProvider.notifier).logout();
                context.go('/login');
              },
            )
          else
            TextButton.icon(
              onPressed: () {
                ref.read(authProvider.notifier).logout();
                context.go('/login');
              },
              icon: const Icon(Icons.logout, color: Colors.white),
              label:
                  const Text('Logout', style: TextStyle(color: Colors.white)),
            ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Welcome, ${authState.userEmail ?? 'User'}!',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 8),
              Text(
                'Upload your bank statement PDF to get AI-powered financial insights.',
                style: TextStyle(color: AppColors.textSecondary),
              ),
              const SizedBox(height: 32),
              // File selection and preview
              if (uploadState.hasFile)
                Expanded(
                  child: Column(
                    children: [
                      Text(
                        uploadState.selectedFilename!,
                        style: const TextStyle(
                            fontWeight: FontWeight.w600, fontSize: 16),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 12),
                      Expanded(
                        child: Card(
                          child: _PdfPreview(
                            bytes: uploadState.selectedBytes!,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      if (isIOS)
                        CupertinoButton(
                          color: AppColors.textSecondary,
                          onPressed: uploadState.isBusy
                              ? null
                              : () =>
                                  ref.read(uploadProvider.notifier).pickFile(),
                          child: const Text('Choose Different PDF'),
                        )
                      else
                        ElevatedButton.icon(
                          onPressed: uploadState.isBusy
                              ? null
                              : () =>
                                  ref.read(uploadProvider.notifier).pickFile(),
                          icon: const Icon(Icons.file_upload_outlined),
                          label: const Text('Choose Different PDF'),
                        ),
                    ],
                  ),
                )
              else
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      children: [
                        Icon(
                          Icons.picture_as_pdf,
                          size: 48,
                          color: AppColors.textSecondary,
                        ),
                        const SizedBox(height: 12),
                        Text(
                          'No file selected',
                          style: TextStyle(color: AppColors.textSecondary),
                        ),
                        const SizedBox(height: 16),
                        if (isIOS)
                          CupertinoButton(
                            color: AppColors.primary,
                            onPressed: uploadState.isBusy
                                ? null
                                : () =>
                                    ref.read(uploadProvider.notifier).pickFile(),
                            child: const Text('Select PDF File'),
                          )
                        else
                          ElevatedButton.icon(
                            onPressed: uploadState.isBusy
                                ? null
                                : () =>
                                    ref.read(uploadProvider.notifier).pickFile(),
                            icon: const Icon(Icons.file_upload_outlined),
                            label: const Text('Select PDF File'),
                          ),
                      ],
                    ),
                  ),
                ),
              const SizedBox(height: 16),
              // Upload button
              if (isIOS)
                CupertinoButton(
                  color: AppColors.accent,
                  onPressed: uploadState.hasFile && !uploadState.isBusy
                      ? () => ref
                          .read(uploadProvider.notifier)
                          .uploadAndProcess()
                      : null,
                  child: const Text('Upload & Analyze'),
                )
              else
                ElevatedButton(
                  onPressed: uploadState.hasFile && !uploadState.isBusy
                      ? () => ref
                          .read(uploadProvider.notifier)
                          .uploadAndProcess()
                      : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.accent,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                  child: const Text('Upload & Analyze',
                      style: TextStyle(fontSize: 16)),
                ),
              const SizedBox(height: 24),
              // Status
              if (uploadState.isUploading)
                _StatusIndicator(
                  text: 'Uploading...',
                  isIOS: isIOS,
                ),
              if (uploadState.isPolling)
                _StatusIndicator(
                  text:
                      'Processing... (${uploadState.pollSeconds}s)',
                  isIOS: isIOS,
                ),
              if (uploadState.error != null)
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.error.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline,
                          color: AppColors.error),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          uploadState.error!,
                          style: const TextStyle(color: AppColors.error),
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatusIndicator extends StatelessWidget {
  final String text;
  final bool isIOS;

  const _StatusIndicator({required this.text, required this.isIOS});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (isIOS)
          const CupertinoActivityIndicator()
        else
          const SizedBox(
            height: 20,
            width: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        const SizedBox(width: 12),
        Text(text, style: TextStyle(color: AppColors.textSecondary)),
      ],
    );
  }
}

class _PdfPreview extends StatefulWidget {
  final Uint8List bytes;

  const _PdfPreview({required this.bytes});

  @override
  State<_PdfPreview> createState() => _PdfPreviewState();
}

class _PdfPreviewState extends State<_PdfPreview> {
  late Future<String> _pdfFilePath;
  int _totalPages = 0;
  int _currentPage = 0;

  @override
  void initState() {
    super.initState();
    _pdfFilePath = _writeBytesToFile(widget.bytes);
  }

  Future<String> _writeBytesToFile(Uint8List bytes) async {
    final dir = await getTemporaryDirectory();
    final file = File('${dir.path}/preview_pdf_${DateTime.now().millisecondsSinceEpoch}.pdf');
    await file.writeAsBytes(bytes);
    return file.path;
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<String>(
      future: _pdfFilePath,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }

        if (snapshot.hasError || !snapshot.hasData) {
          return Center(
            child: Text(
              'Error loading PDF: ${snapshot.error}',
              textAlign: TextAlign.center,
            ),
          );
        }

        final filePath = snapshot.data!;

        return Column(
          children: [
            Expanded(
              child: PDFView(
                filePath: filePath,
                enableSwipe: true,
                swipeHorizontal: false,
                autoSpacing: false,
                pageFling: true,
                fitPolicy: FitPolicy.WIDTH,
                onRender: (pages) {
                  setState(() {
                    _totalPages = pages ?? 0;
                  });
                },
                onPageChanged: (int? page, int? total) {
                  setState(() {
                    _currentPage = page ?? 0;
                  });
                },
              ),
            ),
            if (_totalPages > 0)
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Text(
                  'Page ${_currentPage + 1} of $_totalPages',
                  style: const TextStyle(fontSize: 14, color: AppColors.textSecondary),
                ),
              ),
          ],
        );
      },
    );
  }
}
