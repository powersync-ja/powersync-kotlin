# Ktor Darwin Client (Internal Fork)

This is an internal fork of the [Ktor Darwin HTTP engine](https://github.com/ktorio/ktor) with modifications to address memory issues when processing large HTTP response bodies on Apple platforms.

## Why This Fork Exists

### The Problem: Out of Memory (OOM) on Large Sync Payloads

The upstream Ktor Darwin engine uses an unbounded channel to buffer incoming response data chunks from `NSURLSession`. When processing large sync payloads (hundreds of MBs), this causes:

1. **NSURLSession delivers data faster than it can be processed** - chunks accumulate in the unbounded channel
2. **Memory usage spikes dramatically** - we observed multi-GB allocations during sync operations
3. **OOM crashes on iOS/macOS** - devices run out of memory before the response is fully processed

This issue is specific to the Darwin engine because:

- `NSURLSession` delivers data via delegate callbacks that cannot be paused
- The upstream implementation uses `Channel.UNLIMITED` - buffering all chunks without backpressure
- Other Ktor engines have natural backpressure mechanisms:
  - **OkHttp**: Uses `BufferedSource.read()` which blocks until data is consumed
  - **Apache/Apache5**: Uses `CapacityChannel` for explicit backpressure signaling

### The Solution: Bounded Channel with Backpressure

Our fork modifies `DarwinTaskHandler` to apply backpressure:

```kotlin
// Bounded channel instead of unbounded
private val bodyChunks = Channel<NSData>(capacity = 64)

fun receiveData(dataTask: NSURLSessionDataTask, data: NSData) {
    val result = bodyChunks.trySend(data)
    when {
        result.isClosed -> dataTask.cancel()
        result.isFailure -> {
            // Buffer full - block to apply backpressure
            runBlocking { bodyChunks.send(data) }
        }
    }
}
```

Key changes:

- **Limited channel capacity (64)** - prevents unbounded memory growth
- **`runBlocking` on buffer full** - blocks the NSURLSession delegate thread, naturally slowing data delivery
- **Backpressure propagates to NSURLSession** - the network layer throttles based on processing speed

### Alternative Approaches Considered

**Task Pause/Resume**: We considered using `NSURLSessionTask.suspend()` and `resume()` to pause data delivery when the buffer was full. However, this approach was rejected due to:

- **Complexity** - managing pause/resume state across async boundaries added significant complexity
- **Concurrency issues** - race conditions between pause signals and data delivery callbacks
- **Data delivery timing** - the asynchronous nature of NSURLSession means data can still be delivered after calling `suspend()` on the task, which would require periodic draining and complex state management.
- **Error-prone implementation** - the combination of these factors made the approach fragile and difficult to test

The simpler bounded channel with `runBlocking` approach was chosen as it provides effective backpressure with minimal complexity and maintenance burden.

## When to Update This Fork

This fork should be updated if:

- Ktor releases a fix for this issue upstream (track [ktor issues](https://github.com/ktorio/ktor/issues))
- Security vulnerabilities are found in the Ktor Darwin engine
- New Darwin-specific features are needed

## Files Modified

- `darwin/src/io/ktor/client/engine/darwin/internal/DarwinTaskHandler.kt` - Bounded channel + backpressure logic

## References

- [Original Ktor Darwin Engine](https://github.com/ktorio/ktor/tree/main/ktor-client/ktor-client-darwin)
- PowerSync Kotlin SDK issue: OOM during large sync operations on iOS/macOS
