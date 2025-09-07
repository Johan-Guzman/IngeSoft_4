# ⚙️ Configuración del entorno (JDK + Gradle)

Este proyecto usa **ZeroC ICE** y requiere una versión estable de **Java** y **Gradle** para compilar sin errores.

---

## 1. Instalar **JDK 11**

- Descarga e instala **JDK 11** (Temurin, Zulu u Oracle).  
- En Windows normalmente queda en:  

C:\Program Files\Java\jdk-11



---

## 2. Configurar **JAVA_HOME** en PowerShell

Antes de compilar, abre **PowerShell** en la carpeta del proyecto y ejecuta:

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-11"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
```

## 3. Compilar:
Con JDK 11 y Gradle 6.9.4 configurados, compila normalmente:
```powershell
.\gradlew clean build
```

Para conectarse al server:

```powershell
ssh swarch@192.168.131.119
```

Para conectarse a los clientes:

```powershell
ssh swarch@192.168.131.124
ssh swarch@192.168.131.125
```

Para ejecutar el jar del client:

```powershell
java -jar client/build/libs/client.jar                                                                                                         
```

Para ejecutar el jar del server:

```powershell
java -jar server/build/libs/server.jar
```
