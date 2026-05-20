import xml.etree.ElementTree as ET
from pathlib import Path

def pom_to_gradle(pom_path: Path, output_gradle_dir: Path):
    tree = ET.parse(pom_path)
    root = tree.getroot()
    
    # Namespaces
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    
    # Extract project properties
    group_id_el = root.find("m:groupId", ns)
    if group_id_el is None:
        parent = root.find("m:parent", ns)
        if parent is not None:
            group_id_el = parent.find("m:groupId", ns)
    group_id = group_id_el.text if group_id_el is not None else "dev.springboot4docs"
    
    artifact_id_el = root.find("m:artifactId", ns)
    artifact_id = artifact_id_el.text if artifact_id_el is not None else pom_path.parent.parent.name
    
    java_version_el = root.find(".//m:java.version", ns)
    java_version = java_version_el.text if java_version_el is not None else "25"
    
    # Extract parent Spring Boot version
    parent_el = root.find("m:parent", ns)
    boot_version = "4.0.6"
    if parent_el is not None:
        boot_ver_el = parent_el.find("m:version", ns)
        if boot_ver_el is not None:
            boot_version = boot_ver_el.text
            
    # Extract dependencies
    deps = []
    deps_el = root.find("m:dependencies", ns)
    if deps_el is not None:
        for dep in deps_el.findall("m:dependency", ns):
            g = dep.find("m:groupId", ns).text
            a = dep.find("m:artifactId", ns).text
            
            v_el = dep.find("m:version", ns)
            v = v_el.text if v_el is not None else None
            
            s_el = dep.find("m:scope", ns)
            s = s_el.text if s_el is not None else None
            
            optional_el = dep.find("m:optional", ns)
            opt = optional_el.text == "true" if optional_el is not None else False
            
            deps.append((g, a, v, s, opt))
            
    # Map to gradle dependencies
    gradle_deps = []
    # Always add junit-platform-launcher for tests in Gradle
    gradle_deps.append('    testRuntimeOnly("org.junit.platform:junit-platform-launcher")')
    
    for g, a, v, s, opt in deps:
        # Lombok special case
        if g == "org.projectlombok" and a == "lombok":
            gradle_deps.append('    compileOnly("org.projectlombok:lombok")')
            gradle_deps.append('    annotationProcessor("org.projectlombok:lombok")')
            gradle_deps.append('    testCompileOnly("org.projectlombok:lombok")')
            gradle_deps.append('    testAnnotationProcessor("org.projectlombok:lombok")')
            continue
            
        # Determine configuration
        if s == "test":
            config = "testImplementation"
        elif s == "provided" or opt:
            config = "compileOnly"
        else:
            config = "implementation"
            
        # Construct dependency coordinate
        # If version starts with ${...}, we can check if it is defined in properties
        if v and v.startswith("${") and v.endswith("}"):
            prop_name = v[2:-1]
            prop_el = root.find(f".//m:{prop_name}", ns)
            if prop_el is not None:
                v = prop_el.text
                
        coord = f"{g}:{a}"
        if v:
            coord += f":{v}"
            
        gradle_deps.append(f'    {config}("{coord}")')
        
    # Build build.gradle.kts content
    build_gradle = f"""plugins {{
    java
    id("org.springframework.boot") version "{boot_version}"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "6.25.0"
}}

group = "{group_id}"
version = "0.0.1-SNAPSHOT"

java {{
    toolchain {{
        languageVersion = JavaLanguageVersion.of({java_version})
    }}
}}

repositories {{
    mavenCentral()
}}

dependencies {{
{chr(10).join(gradle_deps)}
}}

tasks.withType<Test> {{
    useJUnitPlatform()
}}

spotless {{
    java {{
        indentWithSpaces(4)
        removeUnusedImports()
    }}
}}
"""
    # Write to target
    output_gradle_dir.mkdir(parents=True, exist_ok=True)
    (output_gradle_dir / "build.gradle.kts").write_text(build_gradle, encoding="utf-8")
    
    settings_gradle = f"""pluginManagement {{
    repositories {{
        mavenCentral()
        gradlePluginPortal()
    }}
}}

plugins {{
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}}

rootProject.name = "{artifact_id}"
"""
    (output_gradle_dir / "settings.gradle.kts").write_text(settings_gradle, encoding="utf-8")

if __name__ == "__main__":
    import sys
    if len(sys.argv) < 3:
        print("Usage: pom_to_gradle.py <pom_path> <output_dir>")
        sys.exit(1)
    pom_to_gradle(Path(sys.argv[1]), Path(sys.argv[2]))
