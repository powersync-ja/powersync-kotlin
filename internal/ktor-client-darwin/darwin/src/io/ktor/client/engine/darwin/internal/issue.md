# OOM Issue Diagnosis - Ktor Darwin Engine

## Problem Summary

Out of Memory (OOM) error occurring during PowerSync sync operations when using the Kotlin Multiplatform SDK from Swift.

## Root Cause

The Ktor Darwin engine's `toByteArray()` function is loading entire HTTP response bodies into memory before processing them. This happens in two steps:

1. **NSURLSession accumulates all response chunks** into a single `NSData` object
2. **Ktor converts the entire NSData to ByteArray** - allocating another full copy

For large sync payloads (hundreds of MBs), this results in massive memory usage (1.6 GB in the reported case).

## Stack Trace Analysis

```
kfun:io.ktor.client.engine.darwin.internal.DarwinTaskHandler#receiveData(...)
    └─ kfun:io.ktor.client.engine.darwin#toByteArray__at__platform.Foundation.NSData(){}kotlin.ByteArray
        └─ kotlin::mm::AllocateArray(...)  // 1.3 GB allocated here!
```

This shows the entire response is being converted to a ByteArray in one allocation.

## Why This Works in Pure Kotlin

When running the same code in pure Kotlin (JVM/Android), Ktor can use:
- OkHttp engine with streaming support
- Direct memory access without NSData conversion
- Incremental processing of response chunks

## Additional Context

The issue is specific to:
- **Platform:** iOS/macOS (Darwin)
- **Engine:** Ktor Darwin engine using NSURLSession
- **Operation:** Large HTTP response body handling
- **Not affected:** Pure Kotlin (JVM/Native) using other engines

## References

- Ktor Darwin Engine: [GitHub - Ktor Darwin](https://github.com/ktorio/ktor)
- PowerSync Kotlin SDK: v1.7.0 (from Package.swift)
- PowerSync Rust Client: Introduced in Swift SDK v1.2.0