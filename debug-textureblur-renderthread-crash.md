# [OPEN] textureblur-renderthread-crash

## Summary
- Symptom: app crashes on startup after calling `textureBlur`
- Current evidence: native crash on Android RenderThread with `SIGSEGV`
- Stack hint: `libhwui.so`, `MiBackgroundBlurBlend::preUpdateInfo`

## Reproduction
1. Launch app
2. Enter composable tree containing `textureBlur`
3. RenderThread crashes with `SIGSEGV`

## Hypotheses
1. Xiaomi/HyperOS vendor blur pipeline has a platform bug when backdrop blur is applied to the current render tree.
2. The captured backdrop and the blur target form a recursive/self-referential render dependency that overflows RenderThread stack.
3. `textureBlur` is being used on a device/API/render path combination that the library nominally supports but the vendor `libhwui` implementation breaks.
4. The blur effect is applied before the sampled backdrop layer is in a stable state, causing invalid layer promotion access in vendor blur code.

## Evidence
- `RenderThread`
- `Fatal signal 11 (SIGSEGV)`
- `Cause: stack pointer is close to top of stack; likely stack overflow.`
- `android::uirenderer::skiapipeline::MiBackgroundBlurBlend::preUpdateInfo`

## Next Step
- Inspect the current composition structure around `layerBackdrop` and `textureBlur`
- Determine whether the blur target is sampling a layer that includes itself or an unstable ancestor subtree
- Propose a minimal structural fix and runtime guard
