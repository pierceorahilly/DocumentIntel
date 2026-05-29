import 'dart:typed_data';

class PdfValidationResult {
  final bool isValid;
  final String? error;

  PdfValidationResult.valid() : isValid = true, error = null;
  PdfValidationResult.invalid(this.error) : isValid = false;
}

class PdfValidator {
  static const int minSizeBytes = 1024; // 1 KB
  static const int maxSizeBytes = 4 * 1024 * 1024; // 4 MB
  static const List<int> pdfMagicBytes = [0x25, 0x50, 0x44, 0x46]; // %PDF

  static PdfValidationResult validate(Uint8List bytes, String filename) {
    // Check extension
    if (!filename.toLowerCase().endsWith('.pdf')) {
      return PdfValidationResult.invalid(
          'Invalid file type. Please select a PDF file.');
    }

    // Check size
    if (bytes.length < minSizeBytes) {
      return PdfValidationResult.invalid(
          'File is too small (${bytes.length} bytes). Minimum size is 1 KB.');
    }
    if (bytes.length > maxSizeBytes) {
      final sizeMb = (bytes.length / (1024 * 1024)).toStringAsFixed(1);
      return PdfValidationResult.invalid(
          'File is too large ($sizeMb MB). Maximum size is 4 MB.');
    }

    // Check magic bytes
    if (bytes.length < 4 ||
        bytes[0] != pdfMagicBytes[0] ||
        bytes[1] != pdfMagicBytes[1] ||
        bytes[2] != pdfMagicBytes[2] ||
        bytes[3] != pdfMagicBytes[3]) {
      return PdfValidationResult.invalid(
          'File does not appear to be a valid PDF.');
    }

    return PdfValidationResult.valid();
  }
}
