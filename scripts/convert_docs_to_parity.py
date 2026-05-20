import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS_DIR = ROOT / "docs"

def main():
    updated_count = 0
    for root, _, files in os.walk(DOCS_DIR):
        for f in files:
            if not f.endswith(".md"):
                continue
            path = Path(root) / f
            content = path.read_text(encoding="utf-8")
            orig_content = content
            
            # 1. Match code/XX-slug/maven cd and run
            pattern_cd_run = r"```bash\s+cd code/(\d+-[a-zA-Z0-9_-]+)/maven\s+\./mvnw\s+spring-boot:run\s*```"
            content = re.sub(
                pattern_cd_run,
                r"""=== "Maven"
    ```bash
    cd code/\1/maven
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    cd code/\1/gradle
    ./gradlew bootRun
    ```""",
                content,
                flags=re.DOTALL
            )
            
            # 2. Match test run with cd
            pattern_cd_test = r"```bash\s+cd code/(\d+-[a-zA-Z0-9_-]+)/maven\s+\./mvnw\s+(?:-q\s+-B\s+)?test\s*```"
            content = re.sub(
                pattern_cd_test,
                r"""=== "Maven"
    ```bash
    cd code/\1/maven
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    cd code/\1/gradle
    ./gradlew test
    ```""",
                content,
                flags=re.DOTALL
            )

            # 3. Match plain test run
            pattern_plain_test = r"```bash\s+\./mvnw\s+(?:-q\s+-B\s+)?test\s*```"
            content = re.sub(
                pattern_plain_test,
                r"""=== "Maven"
    ```bash
    ./mvnw test
    ```

=== "Gradle"
    ```bash
    ./gradlew test
    ```""",
                content,
                flags=re.DOTALL
            )

            # 4. Match plain run command
            pattern_plain_run = r"```bash\s+\./mvnw\s+spring-boot:run\s*```"
            content = re.sub(
                pattern_plain_run,
                r"""=== "Maven"
    ```bash
    ./mvnw spring-boot:run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootRun
    ```""",
                content,
                flags=re.DOTALL
            )

            # 5. Match spring-boot:test-run
            pattern_test_run = r"```bash\s+\./mvnw\s+spring-boot:test-run\s*```"
            content = re.sub(
                pattern_test_run,
                r"""=== "Maven"
    ```bash
    ./mvnw spring-boot:test-run
    ```

=== "Gradle"
    ```bash
    ./gradlew bootTestRun
    ```""",
                content,
                flags=re.DOTALL
            )
            
            # 6. Match spring-boot:test-run with cd
            pattern_cd_test_run = r"```bash\s+cd code/(\d+-[a-zA-Z0-9_-]+)/maven\s+\./mvnw\s+spring-boot:test-run\s*```"
            content = re.sub(
                pattern_cd_test_run,
                r"""=== "Maven"
    ```bash
    cd code/\1/maven
    ./mvnw spring-boot:test-run
    ```

=== "Gradle"
    ```bash
    cd code/\1/gradle
    ./gradlew bootTestRun
    ```""",
                content,
                flags=re.DOTALL
            )

            if content != orig_content:
                print(f"Updated parity tabs in {path.relative_to(ROOT)}")
                path.write_text(content, encoding="utf-8")
                updated_count += 1
                
    print(f"Successfully updated {updated_count} files.")

if __name__ == "__main__":
    main()
