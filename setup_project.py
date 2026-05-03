import os
import shutil
import filecmp

def copy_if_changed(src, dst):
    if os.path.exists(src):
        if not os.path.exists(dst) or not filecmp.cmp(src, dst, shallow=False):
            shutil.copy2(src, dst)
            print(f"Copied: {src} -> {dst}")

# Create directories
base = "app/src/main/java/com/propdf/editor"
for d in ["ui", "ui/viewer", "ui/scanner", "ui/tools", "data/local", "data/repository", "di", "utils"]:
    os.makedirs(os.path.join(base, d), exist_ok=True)

os.makedirs("app/src/main/res/layout", exist_ok=True)
os.makedirs("app/src/main/res/values", exist_ok=True)
os.makedirs("app/src/main/res/xml", exist_ok=True)
os.makedirs("app/src/main/res/menu", exist_ok=True)
os.makedirs("app/src/main/res/drawable", exist_ok=True)
os.makedirs("app/src/main/res/mipmap-hdpi", exist_ok=True)
os.makedirs("app/src/main/res/mipmap-mdpi", exist_ok=True)
os.makedirs("app/src/main/res/mipmap-xhdpi", exist_ok=True)
os.makedirs("app/src/main/res/mipmap-xxhdpi", exist_ok=True)
os.makedirs("app/src/main/res/mipmap-xxxhdpi", exist_ok=True)

# Copy source files
files_map = {
    "MainActivity.kt": f"{base}/ui/MainActivity.kt",
    "ViewerActivity.kt": f"{base}/ui/viewer/ViewerActivity.kt",
    "DocumentScannerActivity.kt": f"{base}/ui/scanner/DocumentScannerActivity.kt",
    "ToolsActivity.kt": f"{base}/ui/tools/ToolsActivity.kt",
    "MainViewModel.kt": f"{base}/ui/MainViewModel.kt",
    "ViewerViewModel.kt": f"{base}/ui/viewer/ViewerViewModel.kt",
    "RecentFileEntity.kt": f"{base}/data/local/RecentFileEntity.kt",
    "RecentFilesDatabase.kt": f"{base}/data/local/RecentFilesDatabase.kt",
    "RecentFilesDao.kt": f"{base}/data/local/RecentFilesDao.kt",
    "RecentFilesRepository.kt": f"{base}/data/repository/RecentFilesRepository.kt",
    "PdfRepository.kt": f"{base}/data/repository/PdfRepository.kt",
    "PdfOperationsManager.kt": f"{base}/data/repository/PdfOperationsManager.kt",
    "OcrManager.kt": f"{base}/data/repository/OcrManager.kt",
    "ScannerProcessor.kt": f"{base}/data/repository/ScannerProcessor.kt",
    "SignatureManager.kt": f"{base}/data/repository/SignatureManager.kt",
    "AiSummaryManager.kt": f"{base}/data/repository/AiSummaryManager.kt",
    "FileHelper.kt": f"{base}/utils/FileHelper.kt",
    "AnnotationCanvasView.kt": f"{base}/ui/viewer/AnnotationCanvasView.kt",
    "SignatureView.kt": f"{base}/ui/viewer/SignatureView.kt",
    "AnnotatedPageView.kt": f"{base}/ui/viewer/AnnotatedPageView.kt",
    "PdfPageAdapter.kt": f"{base}/ui/viewer/PdfPageAdapter.kt",
    "AppModule.kt": f"{base}/di/AppModule.kt",
    "ProPDFApp.kt": f"{base}/ProPDFApp.kt",
    "AndroidManifest.xml": "app/src/main/AndroidManifest.xml",
    "proguard-rules.pro": "app/proguard-rules.pro",
}

# Handle build.gradle - the file in repo root is app_build.gradle, move to app/build.gradle
if os.path.exists("app_build.gradle"):
    copy_if_changed("app_build.gradle", "app/build.gradle")
elif os.path.exists("build.gradle"):
    # If there's a build.gradle in root, check if it's the app build file or root build file
    with open("build.gradle", "r") as f:
        content = f.read()
    if "com.android.application" in content:
        copy_if_changed("build.gradle", "app/build.gradle")

for src, dst in files_map.items():
    copy_if_changed(src, dst)

# Copy resources
if os.path.exists("res"):
    if os.path.exists("app/src/main/res"):
        shutil.rmtree("app/src/main/res")
    shutil.copytree("res", "app/src/main/res")
    print("Copied resources from res/ directory")

# Copy individual XML files
for xml_file, dst_path in [
    ("colors.xml", "app/src/main/res/values/colors.xml"),
    ("themes.xml", "app/src/main/res/values/themes.xml"),
    ("file_paths.xml", "app/src/main/res/xml/file_paths.xml"),
    ("item_pdf_page.xml", "app/src/main/res/layout/item_pdf_page.xml"),
    ("activity_main.xml", "app/src/main/res/layout/activity_main.xml"),
    ("bottom_nav_menu.xml", "app/src/main/res/menu/bottom_nav_menu.xml"),
]:
    copy_if_changed(xml_file, dst_path)

# Create strings.xml if missing
if not os.path.exists("app/src/main/res/values/strings.xml"):
    with open("app/src/main/res/values/strings.xml", "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write('<resources>\n')
        f.write('    <string name="app_name">ProPDF Editor</string>\n')
        f.write('</resources>\n')
    print("Created strings.xml")

# Create settings.gradle if missing
if not os.path.exists("settings.gradle"):
    with open("settings.gradle", "w") as f:
        f.write('pluginManagement {\n')
        f.write('    repositories {\n')
        f.write('        google()\n')
        f.write('        mavenCentral()\n')
        f.write('        gradlePluginPortal()\n')
        f.write('    }\n')
        f.write('}\n')
        f.write('dependencyResolutionManagement {\n')
        f.write('    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n')
        f.write('    repositories {\n')
        f.write('        google()\n')
        f.write('        mavenCentral()\n')
        f.write('    }\n')
        f.write('}\n')
    print("Created settings.gradle")

# Ensure root build.gradle exists (minimal - plugins are defined in settings.gradle or app/build.gradle)
if not os.path.exists("build.gradle"):
    with open("build.gradle", "w") as f:
        f.write("// Root build.gradle - minimal, plugins applied in app/build.gradle\n")
    print("Created root build.gradle")

# Ensure app/build.gradle exists and has the application plugin
if not os.path.exists("app/build.gradle"):
    print("ERROR: app/build.gradle is missing! Cannot build release APK.")
    print("Please ensure app_build.gradle exists in the repository root.")
    exit(1)

# Verify app/build.gradle has the android application plugin
with open("app/build.gradle", "r") as f:
    app_build_content = f.read()

if "com.android.application" not in app_build_content:
    print("ERROR: app/build.gradle missing 'com.android.application' plugin!")
    print("Adding it now...")
    # Prepend the plugin if missing
    with open("app/build.gradle", "w") as f:
        f.write("plugins {\n")
        f.write("    id 'com.android.application'\n")
        f.write("    id 'org.jetbrains.kotlin.android'\n")
        f.write("}\n")
        f.write("\n")
        f.write(app_build_content)
    print("Added missing plugins to app/build.gradle")

print("Setup complete")
