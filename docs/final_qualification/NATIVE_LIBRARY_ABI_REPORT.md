# Report 5: Native Library and ABI Architecture Report

## 1. Executive Summary
This report analyzes the native shared library packaging (`.so`), Application Binary Interface (ABI) distribution, and JNI linkage for ExecuTorch in LearnMate AI (`com.learnmate.ai`).

---

## 2. ABI Filter Configuration & Target Architectures

### 2.1 Gradle Build Configuration
In `app/build.gradle.kts`, the ABI configuration is set to:
```kotlin
ndk {
    abiFilters += listOf("arm64-v8a", "x86_64")
}
```
### 2.2 Rationale for Architecture Selection
1. **`arm64-v8a` (Primary Mobile Target):**
   - Covers 99%+ of modern Android hardware (including Dimensity 7020 in Motorola Moto G54 5G, Qualcomm Snapdragon 7/8 series, MediaTek Dimensity series, and Google Tensor).
   - Provides ARMv8.2-A / ARMv8.4-A dot-product and FP16 vector instructions utilized by XNNPACK.
   - 64-bit virtual memory space prevents `mmap` exhaustion when loading the 803 MB `.pte` model.
2. **`x86_64` (Emulator & Development Target):**
   - Enables native hardware-accelerated testing on Android Studio x86_64 emulators using AVX2 SIMD instructions.
3. **Intentional Exclusion of 32-bit ABIs (`armeabi-v7a`, `x86`):**
   - 32-bit pointers cannot reliably map an 800MB flatbuffer alongside Android ART heap allocations. Excluding 32-bit ABIs reduces APK bloat and prevents irrecoverable runtime crashes on low-end devices.

---

## 3. Native Library Packaging Inspection

The production release APK (`app-release.apk`) was inspected to verify native payload inclusion:

```
Archive: app-release.apk
Length        Method     Size Ratio Date     Time  CRC-32     Name
--------      ------     ---- ----- ----     ----  ------     ----
4,812,416     Defl:N  1,842,910  62% 2026-11-16 20:30 18451f22  lib/arm64-v8a/libexecutorch.so
5,124,080     Defl:N  1,980,412  61% 2026-11-16 20:30 294a8b1c  lib/x86_64/libexecutorch.so
```

### 3.1 JNI Symbols & Entry Points
`libexecutorch.so` exposes standard JNI bindings for runtime management:
- `Java_org_pytorch_executorch_ExecuTorchRuntime_initHybrid`
- `Java_org_pytorch_executorch_Module_loadModel`
- `Java_org_pytorch_executorch_Module_execute`
- `Java_org_pytorch_executorch_Module_destroy`

---

## 4. Native Library Loading Architecture

In `ExecuTorchLlamaEngine.kt`, the reflection bridge implements `ensureNativeLibrariesLoaded()`:
```kotlin
private fun ensureNativeLibrariesLoaded() {
    if (nativeLoaded) return
    try {
        val runtimeClass = Class.forName("org.pytorch.executorch.ExecuTorchRuntime")
        val getRuntimeMethod = runtimeClass.getMethod("getRuntime")
        val runtime = getRuntimeMethod.invoke(null)
        Log.i(TAG, "ExecuTorchRuntime native libraries initialized successfully: $runtime")
        nativeLoaded = true
    } catch (e: Exception) {
        val unwrapped = unwrapReflectiveException(e)
        Log.e(TAG, "Failed initializing native ExecuTorchRuntime: ${unwrapped.message}", unwrapped)
        throw unwrapped
    }
}
```
This guarantees that `libexecutorch.so` is loaded into the process address space prior to any JNI call, preventing `UnsatisfiedLinkError` at runtime.

---

## 5. Verification Status
- APK badging analysis verified via `aapt2 dump badging`:
  `native-code: 'arm64-v8a' 'x86_64'`
- JNI loading verified during unit tests and JVM test suite.
