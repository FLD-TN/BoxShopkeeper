# BoxShopkeeper

Item-for-item trading shops with an in-game editor

## Build

```
./gradlew build
```

Repo chỉ có mã nguồn, không chứa file jar nào (trừ `gradle/wrapper/gradle-wrapper.jar` chuẩn của Gradle).
Trước khi build, tự đặt các jar sau vào thư mục `libs/` ở gốc repo:

- `MMOItems-6.10.jar` - jar plugin MMOItems
- `MythicLib-1.7.jar` - jar plugin MythicLib
