# Native shader preservation after the failed v0.12.25 phone test

The user's v0.12.25 log reports 3,889 GPU-complete and posted frames with no fence failures, while screenshots show missing text fragments and corrupted textures. Display handoff corrections did not fix the visible problem. Do not equate delivered frames with correct pixels.

The pinned Vortek shader inspector rewrites scaled vertex formats to integer formats while lazily compiling a shared ShaderModule. Later pipelines skip the format rewrite when the module is already compiled. The same immutable Vulkan shader can therefore be paired with incompatible vertex formats depending on pipeline creation order. Parallel creation can also mutate and free the same retained SPIR-V.

For shadPS4 Vulkan on a native driver supporting every affected scaled vertex format and shader clip distance, preserve its shaders and vertex formats. Create the native module at vkCreateShaderModule time; pipeline creation only unwraps the already-created handle. Retain the existing compatibility path for other engines and unsupported native capabilities. A test must reproduce the baseline discrepancy and exercise repeated and concurrent pipeline creation on the preservation path. Do not claim this is the proven cause of the user's complete rendering failure: no on-device draw capture is available.

Keep the emulator runtime, both Turnip drivers, display ownership fix, audio, input and application ID. Use versionCode 32 / versionName 0.12.26-native-shaders. Correct the hardcoded crash-register diagnostic flag to describe the actual Play/probe setting. No game or save changes. Verify CI and update signing lineage, and distinguish host regression results from phone rendering.
