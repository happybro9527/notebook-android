[app]
title = 笔记本
package.name = notebook
package.domain = org.notebook
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,html,xml,java,gradle,properties
main.class = com.notebook.MainActivity
android.manifest = app/src/main/AndroidManifest.xml
android.gradle_dependencies = androidx.appcompat:appcompat:1.6.1,androidx.activity:activity:1.7.2,androidx.core:core:1.10.1
android.permissions = RECORD_AUDIO
android.api = 33
android.minapi = 23
android.targetapi = 33
android.build_tools_version = 33.0.2
android.entrypoint = com.notebook.MainActivity
android.archs = arm64-v8a, armeabi-v7a, x86_64
android.add_src = app/src/main/java
android.add_assets = app/src/main/assets
android.add_res = app/src/main/res
android.use_gradle = True

[buildozer]
log_level = 2
warn_on_root = 1
