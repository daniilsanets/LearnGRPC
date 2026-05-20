🚀 Быстрый старт с gRPC на Java (Maven)

Это пошаговое руководство и шаблон проекта для реализации межсервисного взаимодействия с использованием gRPC и Protocol Buffers (Protobuf) на Java.

В отличие от классического REST (передача JSON по HTTP/1.1), gRPC использует бинарный формат данных и работает поверх HTTP/2, что обеспечивает строгую типизацию, высокую скорость и поддержку стриминга.

📁 Правильная структура проекта

grpc-demo/
├── src/
│   ├── main/
│   │   ├── java/com/example/grpc/
│   │   │   ├── PingPongServiceImpl.java  # 4. Логика сервиса
│   │   │   ├── GrpcServer.java           # 5. Запуск сервера
│   │   │   └── GrpcClient.java           # 6. Клиент для проверки
│   │   └── proto/
│   │       └── pingpong.proto            # 2. Файл контракта
├── pom.xml                               # 1. Зависимости и плагины
└── README.md


(Внимание: папка target создается автоматически, писать код в ней нельзя!)

🛠 Шаг 1: Настройка Maven (pom.xml)

Добавьте необходимые зависимости (Netty, gRPC, Protobuf) и плагин для автоматической генерации Java-кода.

<project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)" 
         xmlns:xsi="[http://www.w3.org/2001/XMLSchema-instance](http://www.w3.org/2001/XMLSchema-instance)"
         xsi:schemaLocation="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0) [http://maven.apache.org/xsd/maven-maven-4.0.0.xsd](http://maven.apache.org/xsd/maven-maven-4.0.0.xsd)">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>grpc-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <grpc.version>1.62.2</grpc.version>
        <protobuf.version>3.25.3</protobuf.version>
    </properties>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.7.1</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.6.1</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-netty-shaded</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-protobuf</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-stub</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>javax.annotation</groupId>
            <artifactId>javax.annotation-api</artifactId>
            <version>1.3.2</version>
        </dependency>
    </dependencies>
</project>


📝 Шаг 2: Создание контракта (Protobuf)

Создайте файл src/main/proto/pingpong.proto. Этот файл является "источником правды" для сервера и клиента.

syntax = "proto3";

option java_multiple_files = true;
package com.example.grpc;

message PingRequest {
  string message = 1; // 1 — бинарный тег поля
}

message PongResponse {
  string message = 2; // 2 — бинарный тег поля
}

service PingPongService {
  rpc ping(PingRequest) returns (PongResponse);
}


⚙️ Шаг 3: Генерация кода

Выполните команду в терминале (или через панель Maven в IDE):

mvn clean compile


Плагин создаст Java-классы на основе вашего .proto файла в директории target/generated-sources/protobuf/.

🖥 Шаг 4: Реализация Сервера

Создайте класс PingPongServiceImpl.java в src/main/java/com/example/grpc/.

package com.example.grpc;

import io.grpc.stub.StreamObserver;

public class PingPongServiceImpl extends PingPongServiceGrpc.PingPongServiceImplBase {

    @Override
    public void ping(PingRequest request, StreamObserver<PongResponse> responseObserver) {
        String clientMessage = request.getMessage();
        System.out.println("[СЕРВЕР] Получено: " + clientMessage);

        PongResponse response = PongResponse.newBuilder()
                .setMessage("ПОНГ! Вы прислали: " + clientMessage)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted(); // Сигнал завершения
    }
}


Создайте точку входа для сервера — GrpcServer.java:

package com.example.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;

public class GrpcServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(9090)
                .addService(new PingPongServiceImpl())
                .build();

        server.start();
        System.out.println("[СЕРВЕР] Запущен на порту 9090...");
        server.awaitTermination();
    }
}


📱 Шаг 5: Реализация Клиента

Создайте GrpcClient.java для проверки работы:

package com.example.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class GrpcClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext() // Выключаем TLS для локального теста
                .build();

        PingPongServiceGrpc.PingPongServiceBlockingStub stub = PingPongServiceGrpc.newBlockingStub(channel);

        PingRequest request = PingRequest.newBuilder()
                .setMessage("ПИНГ")
                .build();

        PongResponse response = stub.ping(request);
        System.out.println("[КЛИЕНТ] Ответ от сервера: " + response.getMessage());

        channel.shutdown();
    }
}


🚀 Шаг 6: Запуск приложения

Запустите метод main в классе GrpcServer.

Убедитесь, что в консоли появилось сообщение об успешном старте.

Запустите метод main в классе GrpcClient.

Наслаждайтесь мгновенным бинарным обменом данными!

🚑 Частые проблемы (Troubleshooting)

IDE не видит сгенерированные классы (красные подчеркивания):
В IntelliJ IDEA: Правый клик по папке target/generated-sources/protobuf/ -> Mark Directory as -> Generated Sources Root.

Я случайно написал код в папке target:
Всё, что находится в папке target, удаляется при сборке проекта (mvn clean). Всегда пишите код только в src/main/java/.

Ошибка компиляции Protobuf:
Проверьте, что путь к файлу строго src/main/proto/. Плагин ищет .proto файлы именно там.
