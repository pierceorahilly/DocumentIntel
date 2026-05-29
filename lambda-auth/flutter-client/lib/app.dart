import 'dart:io';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/router.dart';
import 'core/theme/app_theme.dart';

class TrustalApp extends ConsumerWidget {
  const TrustalApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);

    if (Platform.isIOS) {
      return CupertinoApp.router(
        title: 'Trustal',
        theme: AppTheme.cupertinoTheme,
        routerConfig: router,
        debugShowCheckedModeBanner: false,
      );
    }

    return MaterialApp.router(
      title: 'Trustal',
      theme: AppTheme.materialTheme,
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
