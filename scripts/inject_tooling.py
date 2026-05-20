import os
import shutil
import re
from pathlib import Path
from pom_to_gradle import pom_to_gradle

ROOT = Path(__file__).resolve().parent.parent
TEMPLATE_DIR = ROOT / "scripts/gradle-wrapper-template"

def clean_and_inject_pom(pom_path: Path):
    if not pom_path.exists():
        return
    content = pom_path.read_text(encoding="utf-8")
    
    # Remove spotbugs-maven-plugin if exists
    pattern_spotbugs = r"\s*<plugin>\s*<groupId>com\.github\.spotbugs</groupId>.*?<artifactId>spotbugs-maven-plugin</artifactId>.*?</plugin>"
    content = re.sub(pattern_spotbugs, "", content, flags=re.DOTALL)
    
    # Remove spotless-maven-plugin if exists
    pattern_spotless = r"\s*<plugin>\s*<groupId>com\.diffplug\.spotless</groupId>.*?<artifactId>spotless-maven-plugin</artifactId>.*?</plugin>"
    content = re.sub(pattern_spotless, "", content, flags=re.DOTALL)
    
    print(f"Injecting Spotless into {pom_path.relative_to(ROOT)}...")
    
    plugins_inject = """            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>2.43.0</version>
                <configuration>
                    <java>
                        <indent>
                            <spaces>true</spaces>
                            <spacesPerTab>4</spacesPerTab>
                        </indent>
                        <removeUnusedImports/>
                    </java>
                </configuration>
            </plugin>
        </plugins>"""
        
    if "</plugins>" in content:
        parts = content.rsplit("</plugins>", 1)
        content = plugins_inject.join(parts)
        
    pom_path.write_text(content, encoding="utf-8")

def restore_gradle(chapter_dir: Path):
    maven_dir = chapter_dir / "maven"
    gradle_dir = chapter_dir / "gradle"
    
    if not maven_dir.exists():
        return
        
    # Recreate gradle/ directory
    if gradle_dir.exists():
        shutil.rmtree(gradle_dir)
    gradle_dir.mkdir(parents=True)
    
    # Copy wrapper files
    shutil.copy(TEMPLATE_DIR / "gradlew", gradle_dir / "gradlew")
    shutil.copy(TEMPLATE_DIR / "gradlew.bat", gradle_dir / "gradlew.bat")
    shutil.copytree(TEMPLATE_DIR / "gradle", gradle_dir / "gradle", dirs_exist_ok=True)
    
    # Make gradlew executable
    os.chmod(gradle_dir / "gradlew", 0o755)
    
    # Copy src/ directory from maven
    maven_src = maven_dir / "src"
    if maven_src.exists():
        shutil.copytree(maven_src, gradle_dir / "src")
        
    # Generate build.gradle.kts & settings.gradle.kts
    pom_path = maven_dir / "pom.xml"
    if pom_path.exists():
        pom_to_gradle(pom_path, gradle_dir)

def main():
    code_dir = ROOT / "code"
    for d in sorted(code_dir.iterdir()):
        if d.is_dir() and d.name[0].isdigit():
            pom_path = d / "maven/pom.xml"
            clean_and_inject_pom(pom_path)
            restore_gradle(d)

if __name__ == "__main__":
    main()
