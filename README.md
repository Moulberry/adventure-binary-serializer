# Adventure Binary Serializer

A library for [adventure](https://github.com/KyoriPowered/adventure) that serializes Components to packed bytes  
The format is intended for transmission over a network or short-term storage  

# Gradle
Step 1. Add JitPack as a repository to your root build.gradle
```
repositories {
    ...
    maven { url 'https://jitpack.io' }
}
```
Step 2. Add Adventure Binary Serializer as a dependency

```
dependencies {
    implementation("com.github.Moulberry:adventure-binary-serializer:-SNAPSHOT")
}
```

## Advantages compared to GSON Serializer
The packed byte format is smaller than the JSON format, making it useful for transmission over a network (eg. chat between servers) or short-term storage  

## Warning
This format should not be used for long-term storage of Components  
This library does **NOT** guarantee back-compatibility between Component versions  
