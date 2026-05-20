import os
import shutil
from pathlib import Path
from pom_to_gradle import pom_to_gradle

ROOT = Path(__file__).resolve().parent.parent
TEMPLATE_DIR = ROOT / "scripts/gradle-wrapper-template"

def restore_gradle(chapter_dir: Path):
    maven_dir = chapter_dir / "maven"
    gradle_dir = chapter_dir / "gradle"
    
    if not maven_dir.exists():
        return
        
    print(f"Restoring Gradle for {chapter_dir.name}...")
    
    # 1. Recreate gradle/ directory
    if gradle_dir.exists():
        shutil.rmtree(gradle_dir)
    gradle_dir.mkdir(parents=True)
    
    # 2. Copy wrapper files
    shutil.copy(TEMPLATE_DIR / "gradlew", gradle_dir / "gradlew")
    shutil.copy(TEMPLATE_DIR / "gradlew.bat", gradle_dir / "gradlew.bat")
    shutil.copytree(TEMPLATE_DIR / "gradle", gradle_dir / "gradle", dirs_exist_ok=True)
    
    # Make gradlew executable
    os.chmod(gradle_dir / "gradlew", 0o755)
    
    # 3. Copy src/ directory from maven
    maven_src = maven_dir / "src"
    if maven_src.exists():
        shutil.copytree(maven_src, gradle_dir / "src")
        
    # 4. Generate build.gradle.kts & settings.gradle.kts
    pom_path = maven_dir / "pom.xml"
    if pom_path.exists():
        pom_to_gradle(pom_path, gradle_dir)

def main():
    code_dir = ROOT / "code"
    for d in sorted(code_dir.iterdir()):
        if d.is_dir() and d.name[0].isdigit():
            restore_gradle(d)

if __name__ == "__main__":
    main()
