# TNA Android

Kotlin + Jetpack Compose клиент Travel Never Alone. Это мобильный близнец `TNAiOS`: те же экраны, те же тексты, тот же Supabase (PostgREST и RPC, без Supabase SDK).

## Паритет с iOS

Любое изменение клиентской фичи делается **в обоих** приложениях в одном заходе:

- `TNAiOS/` — SwiftUI
- `TNAAndroid/` — этот репозиторий

Общие контракты не расходятся: доменные правила (роли, сезоны, фильтр каталога, Компас, бонусы, телефон, непрочитанные), имена аналитики, колонки PostgREST и RPC. Мок-каталог зеркалит `MockData.swift` и `seed.sql`.

Пуши: бэкенд сейчас принимает APNs (`platform=ios`). Экран уведомлений на Android есть; FCM-токен не отправляем, пока edge-функция `register-push-token` не примет `platform=android`.

## Сборка

Нужны JDK 17+ и Android SDK 35.

```
./gradlew :app:assembleBetaDebug
./gradlew :app:testBetaDebugUnitTest
```

Раздача APK и публикация в Play с нуля: `ДИСТРИБУЦИЯ.md`.

Флейворы, как схемы iOS:

- `beta` — `com.travelneveralone.android.beta`, тестовый Supabase
- `prod` — `com.travelneveralone.android`, боевой Supabase

`AppConfig.useMock` переключает фикстуры и живой бэкенд (по умолчанию живой, как в iOS).

## Архитектура

MVVM, одна точка сборки зависимостей в `AppModel` (аналог `AppCoordinator` + `AppDependencies`). Экраны не ходят в сеть сами: каталог, чаты, паспорт, брони, роли — через `TnaRepository`. Чистая логика без Android лежит в `Models.kt` и `CatalogLogic.kt` и покрыта `DomainRulesTest`.

## UI = порт iOS, файл в файл

Вёрстка повторяет SwiftUI-экраны один в один (те же блоки, порядок, тексты, отступы, цвета). Меняешь экран в `TNAiOS/TNA/Features/...` — правь соответствующий файл здесь:

- `Theme.kt` ← `Core/DesignSystem` (палитра, `displayStyle`, `pressable` вместо `PressableStyle`, `shimmer`, `sfIcon` — SF Symbol из бэкенда → Material-иконка, `TourCover`, `Avatar`, `FireBadge`, `stampBorder`)
- `Screens.kt` ← `App/` (Root, Splash, TabBar, плашка «Офлайн/Нет связи») + `Onboarding`, `SheetHost`
- `CatalogScreen.kt` ← `Catalog/` (+ `TourCards`), `TourDetailScreen.kt` ← `TourDetail/`, `BookingSheet.kt` ← `Booking/`
- `PassportScreen.kt` ← `Passport/`, `ChatScreens.kt` ← `Chats/`, `ProfileScreen.kt` ← `Profile/ProfileView`
- `Sheets.kt` ← остальные шторки (Auth, News, MyBookings, Info/*, Notifications, Companions, Countries, EditProfile, Compass, Schedule, Trip)
- `Media.kt` — полноэкранный просмотр фото

Правила: никаких эмодзи вместо иконок (только `sfIcon`), никаких моков в живом режиме (`Mock.*` только при `AppConfig.useMock`), состояние, которое видит UI, — только Compose state (`mutableStateOf`), иначе экран не перерисуется.

Пока нет на Android (есть в iOS): видео на карточках карусели, голосовые, создание опросов и подсчёт голосов, закрепы в чате, мини-профиль из рейтинга, «Итоги года» и шаринг паспорта, документы у попутчиков и в профиле, трип-пак (рейсы, чек-лист), обрезка аватарки.
