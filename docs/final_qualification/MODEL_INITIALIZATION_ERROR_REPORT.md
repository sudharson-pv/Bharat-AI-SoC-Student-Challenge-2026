# Report 6: Model Initialization Error Handling & Unwrapping Report

## 1. Executive Summary
This report analyzes the diagnostic enhancements made to error reporting during ExecuTorch model initialization and execution in LearnMate AI (`com.learnmate.ai`). Prior to this fix, Java reflection obscured underlying native runtime crashes within generic `InvocationTargetException` envelopes, complicating root-cause analysis on mobile devices.

---

## 2. Root Cause: Reflective Exception Masking

### 2.1 The Problem
ExecuTorch's Java API is accessed via dynamic reflection in `ReflectionExecuTorchLlmBridge` to maintain loose coupling across build variants. When a reflective call like `loadMethod.invoke(instance, modelPath, ...)` fails, the JVM wraps any checked exception, unchecked exception, or `Error` (including native JNI aborts and `OutOfMemoryError`) inside:
```
java.lang.reflect.InvocationTargetException
    at java.lang.reflect.Method.invoke(Native Method)
    at com.learnmate.ai.ai.ReflectionExecuTorchLlmBridge.loadModel(...)
```
Previously, error catch blocks logged `e.message`, which was frequently `null` or meaningless for `InvocationTargetException`. The actual failure—such as corrupt flatbuffers, invalid tensor descriptors, missing native symbols, or RAM allocation failures—was hidden from developers and end-users.

---

## 3. The Unwrapping Architecture

### 3.1 Recursive Exception Extractor
In `ExecuTorchLlamaEngine.kt` and `ModelReadinessAuditor.kt`, recursive unwrapping was introduced:
```kotlin
private fun unwrapReflectiveException(e: Throwable): Throwable {
    var cause: Throwable? = e
    while (cause is java.lang.reflect.InvocationTargetException && cause.targetException != null) {
        cause = cause.targetException
    }
    return cause ?: e
}
```

### 3.2 Categorized Error Surfacing
The unwrapped exception is categorized and bound to the engine state:

| Underlying Error Type | Source | Handled State | User-Facing Diagnostics |
| :--- | :--- | :--- | :--- |
| `UnsatisfiedLinkError` | Native library missing or incompatible ABI | `INITIALIZATION_FAILED` | "Native library (libexecutorch.so) failed to load for ABI. Ensure 64-bit arm64-v8a device." |
| `OutOfMemoryError` | Physical RAM exhaustion or 32-bit address space limit | `INITIALIZATION_FAILED` | "Out of memory during model allocation. Ensure device has at least 1.5 GB available RAM." |
| `ExecutorchRuntimeException` | FlatBuffer corrupt, wrong format, or tensor mismatch | `INITIALIZATION_FAILED` | "ExecuTorch runtime error: [Native error message]. Verify model SHA-256 integrity." |
| `FileNotFoundException` | Weights or tokenizer path not found | `NOT_INSTALLED` | "Model weights or tokenizer file not found on disk at [Path]." |
| `SecurityException` | Storage access violation | `INITIALIZATION_FAILED` | "Filesystem permission denied accessing internal model sandbox." |

---

## 4. State Machine Transition Safeguards

When an error occurs during initialization:
1. `ExecuTorchLlamaEngine.isModelLoaded` is set to `false`.
2. `runtimeState` is transitioned to `ModelRuntimeState.INITIALIZATION_FAILED`.
3. `lastError` stores the specific, detailed unwrapped error message.
4. Any active generation requests fail gracefully and report the failure explicitly without silent fallbacks.
5. The UI reacts instantaneously, enabling "Retry Initialization" and "View Detailed Diagnostics" buttons.

---

## 5. Verification Status
- Unit tests validating exception unwrapping: Passed.
- Reflection bridge robustness verified across mock and production configurations.
