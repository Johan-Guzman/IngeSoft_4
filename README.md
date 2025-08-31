Configuración del entorno (JDK + Gradle)

Este proyecto usa ZeroC ICE y requiere una versión estable de Java y Gradle para compilar sin errores.

1. Instalar JDK 11

Descarga e instala JDK 11 (Temurin, Zulu u Oracle).

En Windows normalmente queda en:

C:\Program Files\Java\jdk-11

2. Configurar JAVA_HOME en PowerShell

Antes de compilar, abre PowerShell en la carpeta del proyecto y ejecuta:

$env:JAVA_HOME="C:\Program Files\Java\jdk-11"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version


Debe mostrar algo como:

openjdk version "11.0.x" ...

3. Usar Gradle 6.9.4

El plugin ICE no funciona bien con Gradle 7/8.
Configura el wrapper de Gradle a la versión 6.9.4 con:

.\gradlew wrapper --gradle-version 6.9.4


Esto actualizará el archivo:

gradle/wrapper/gradle-wrapper.properties


para que siempre use Gradle 6.9.4.

4. Compilar

Con JDK 11 y Gradle 6.9.4 configurados, compila normalmente:

.\gradlew clean build
