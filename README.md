# LearnGrpc — учебный проект по gRPC (Java)

Минимальный пример **Unary RPC**: клиент отправляет `PingRequest`, сервер отвечает `PongResponse`. Код генерируется из `.proto` через Maven.

## Что такое gRPC

**gRPC** (Google Remote Procedure Call) — фреймворк для вызова удалённых процедур между сервисами:

- контракт API описывается в **Protocol Buffers** (`.proto`);
- по сети ходит **бинарный** protobuf, а не JSON;
- транспорт — **HTTP/2** (мультиплексирование, стримы, заголовки);
- есть **сгенерированные** клиент и сервер (stub / service base) для Java, Go, C#, Python и др.

Схема этого репозитория:

```
┌─────────────┐     HTTP/2 + protobuf      ┌─────────────┐
│ GrpcClient  │ ─────────────────────────► │ GrpcServer  │
│  :9090      │   rpc ping(PingRequest)    │ PingPong    │
│             │ ◄───────────────────────── │ ServiceImpl │
└─────────────┘     PongResponse           └─────────────┘
```

## Когда использовать gRPC

| Подходит | Слабее / не подходит |
|----------|----------------------|
| **Микросервисы** внутри дата-центра / VPC (низкая задержка, много вызовов) | Публичное API для браузеров без **gRPC-Web** и прокси |
| **Строгий контракт** и генерация кода из `.proto` в разных языках | Нужен «просто curl» и читаемый JSON без инструментов |
| **Стриминг**: сервер → клиент, клиент → сервер, двунаправленный | Кэширование на CDN как у GET REST |
| **Высокая пропускная способность**, компактные сообщения | Редкие вызовы, где важнее простота REST, а не миллисекунды |
| **Долгоживущие соединения** и push с сервера | Жёсткие корпоративные прокси только под HTTP/1.1 |

**Практическое правило:** внутри backend-to-backend — часто gRPC; наружу к мобильным/веб-клиентам — REST/GraphQL или gRPC-Web + gateway.

## Четыре типа RPC (в этом проекте — только первый)

| Тип | Сигнатура в `.proto` | Когда нужен |
|-----|----------------------|-------------|
| **Unary** | `rpc ping(A) returns (B);` | Один запрос — один ответ (как REST POST) — **реализовано здесь** |
| **Server streaming** | `rpc list(Req) returns (stream Item);` | Поток данных с сервера (логи, тики, каталог) |
| **Client streaming** | `rpc upload(stream Chunk) returns (Summary);` | Загрузка файла, пакетная отправка |
| **Bidirectional streaming** | `rpc chat(stream Msg) returns (stream Msg);` | Чат, координация, real-time |

## Структура проекта

```
LearnGrpc/
├── pom.xml                          # gRPC, protobuf, protobuf-maven-plugin
├── src/main/proto/ping_pong.proto   # контракт API
└── src/main/java/com/danechka/grpc/
    ├── GrpcServer.java              # порт 9090
    ├── GrpcClient.java              # blocking stub
    └── PingPongServiceImpl.java     # реализация rpc ping
```

После `mvn compile` Maven генерирует в `target/generated-sources/protobuf/`:

- `PingRequest`, `PongResponse` — сообщения;
- `PingPongServiceGrpc` — stub (клиент) и `PingPongServiceImplBase` (сервер).

## Требования

- **JDK 21** (см. `pom.xml`)
- **Maven 3.6+**
- Свободный порт **9090** на localhost

## Быстрый старт

### Вариант A — IntelliJ IDEA (проще всего)

1. Откройте проект `LearnGrpc`.
2. `mvn compile` или **Build → Build Project** (нужна генерация классов из `.proto`).
3. Запустите **Run** на `GrpcServer` — дождитесь `Server started, listening on 9090`.
4. Запустите **Run** на `GrpcClient`.

### Вариант B — командная строка

Из каталога `LearnGrpc`:

```bash
mvn compile
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
set /p CP=<cp.txt
java -cp "target/classes;target/generated-sources/protobuf/grpc-java;target/generated-sources/protobuf/java;%CP%" com.danechka.grpc.GrpcServer
```

Во втором терминале (сервер уже запущен):

```bash
java -cp "target/classes;target/generated-sources/protobuf/grpc-java;target/generated-sources/protobuf/java;%CP%" com.danechka.grpc.GrpcClient
```

На Linux/macOS замените `;` на `:` и `set /p CP=<cp.txt` на `export CP=$(cat cp.txt)`.

Ожидаемый вывод:

- сервер: `Server started, listening on 9090` и лог входящего сообщения;
- клиент: отправка `hello from client :)` и ответ с `PONG!!!`.

## Как устроен контракт (`.proto`)

Файл `src/main/proto/ping_pong.proto`:

- `syntax = "proto3"` — текущая версия protobuf;
- `package com.danechka.grpc` — namespace для Java;
- `java_multiple_files = true` — отдельный `.java` на каждое message/service;
- поля `message` нумеруются **1, 2, 3…** — номера нельзя менять задним числом без миграции (см. подводные камни).

Сервис:

```protobuf
service PingPongService {
  rpc ping(PingRequest) returns (PongResponse);
}
```

## Цепочка вызова (что повторить в голове)

1. **Клиент:** `ManagedChannel` → `PingPongServiceBlockingStub` → `stub.ping(request)`.
2. **Сеть:** HTTP/2, путь вида `/com.danechka.grpc.PingPongService/ping`, тело — protobuf.
3. **Сервер:** `ServerBuilder` + `addService(new PingPongServiceImpl())`.
4. **Обработчик:** `onNext(response)` → `onCompleted()` (обязательно завершить unary-вызов).

## Подводные камни

### 1. HTTP/2 и инфраструктура

gRPC **требует HTTP/2**. Старые балансировщики, некоторые nginx без `grpc_pass`, корпоративные proxy могут ломать или буферизовать стримы. Для production обычно: L7 LB с поддержкой gRPC, или **gRPC-Gateway** / Envoy.

### 2. TLS и `usePlaintext()`

В учебном клиенте:

```java
.usePlaintext()  // без шифрования — только localhost / dev
```

В production: TLS (`useTransportSecurity()`), сертификаты, часто **mTLS** между сервисами. Без TLS данные и метаданные идут открытым текстом.

### 3. Обратная совместимость protobuf

- **Не переиспользуйте** номера полей под другой тип.
- **Не удаляйте** поля — помечайте `reserved` или оставляйте с `deprecated`.
- Новые поля добавляйте с **новыми** номерами; старые клиенты их игнорируют (по умолчанию).
- Изменение `string` → `int32` с тем же номером — поломка на уровне wire format.

### 4. Deadlines и отмена

Unary без deadline может висеть вечно. В production:

```java
stub.withDeadlineAfter(5, TimeUnit.SECONDS).ping(request);
```

На сервере проверяйте `Context.current().isCancelled()`.

### 5. Blocking vs async stub

| Stub | Поведение |
|------|-----------|
| `BlockingStub` | Поток блокируется — просто для обучения |
| `Stub` (async) | Callback / `ListenableFuture` |
| `FutureStub` | `ListenableFuture` для unary |

Для server streaming blocking stub **не подходит** — нужен async.

### 6. Ошибки: Status, не исключения HTTP

Ошибки — `io.grpc.Status` (`NOT_FOUND`, `INVALID_ARGUMENT`, `DEADLINE_EXCEEDED`). На сервере:

```java
responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("...").asException());
```

Клиент ловит `StatusRuntimeException`.

### 7. Размер сообщений

Дефолтный лимит ~4 MB. Большие payload — chunking через **client streaming** или хранение в S3 + ссылка в protobuf.

### 8. Версии зависимостей

`grpc`, `protoc`, `protoc-gen-grpc-java` должны быть **согласованы** (в `pom.xml` одна версия `grpc.version`). Иначе — странные ошибки компиляции или runtime.

### 9. `javax.annotation` / Java 9+

`@Generated` и др. требуют `javax.annotation-api` (уже в `pom.xml`) — без этого сборка на новых JDK падает.

### 10. Graceful shutdown

Учебный код делает `channel.shutdown()`, но не `awaitTermination`. В production:

```java
channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
```

Иначе — обрыв активных RPC при деплое.

### 11. Идемпотентность и ретраи

Повтор unary-запроса может выполнить операцию дважды. Для неидемпотентных методов настраивайте retry policy осторожно (gRPC Java retry — отдельная тема).

### 12. Observability

REST привычен к access-log. В gRPC нужны **interceptors**, OpenTelemetry, распространение `trace-id` в **metadata** (аналог HTTP-заголовков).

## gRPC vs REST (кратко)

| | gRPC | REST (JSON) |
|---|------|-------------|
| Формат | Protobuf (бинарный) | JSON (текст) |
| Контракт | `.proto` + codegen | OpenAPI / ad hoc |
| Транспорт | HTTP/2 | чаще HTTP/1.1 |
| Стриминг | нативно | SSE/WebSocket отдельно |
| Браузер | gRPC-Web + proxy | нативно |

## Куда двигаться после этого репо

1. Добавить в `.proto` **server streaming** (например, поток чисел).
2. Заменить `BlockingStub` на **async** + `StreamObserver`.
3. Включить **TLS** (self-signed для localhost).
4. **Metadata**: передать `authorization` / `request-id` в заголовках.
5. **Deadline** и обработка `DEADLINE_EXCEEDED`.
6. **Server interceptor** для логирования и метрик.

## Полезные ссылки

- [Официальная документация gRPC](https://grpc.io/docs/)
- [gRPC Java quick start](https://grpc.io/docs/languages/java/quickstart/)
- [Protocol Buffers](https://protobuf.dev/overview/)
- [gRPC concepts](https://grpc.io/docs/what-is-grpc/core-concepts/)
- [Style guide для .proto](https://protobuf.dev/programming-guides/style/)

