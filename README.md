# Лабораторная работа #5

## Implementación de la variante v4: biblioteca

Este repositorio parte de la aplicación del lab4 y protege el gateway y los tres servicios (library, rating y reservation) con JWT firmados por Keycloak. Cada servicio descarga y almacena las claves públicas JWKS y valida localmente firma, emisor, audiencia y fechas del token. El gateway reenvía el mismo bearer token a los servicios internos.

### Ejecutar localmente

Requisitos: Docker Desktop con Compose y Python 3.

```sh
docker compose up --build -d
python scripts/wait-ready.py
python scripts/security-smoke.py
```

La pila usa los puertos 38050 (rating), 38060 (library), 38070 (reservation), 38080 (gateway), 38090 (Keycloak) y 35432 (PostgreSQL), así puede convivir con los contenedores del lab4 que usan los puertos estándar. Se pueden cambiar con RATING_PORT, LIBRARY_PORT, RESERVATION_PORT, GATEWAY_PORT, KEYCLOAK_PORT y POSTGRES_PORT.

La configuración local importa un realm de demostración con el usuario testmax / lab5-local-password y el cliente library-client / lab5-local-client-secret. No uses estas credenciales de ejemplo en una instalación pública. El entorno Postman para la variante v4 apunta al gateway local. Para ejecutar las pruebas originales:

```sh
newman run v4/postman/collection.json -e v4/postman/environment.json
```

Las API de los cuatro servicios responden 401 si falta el token o si este no es válido. Los endpoints de salud quedan públicos. La identidad se obtiene del claim preferred_username; enviar X-User-Name no cambia el usuario.

### Proveedor OIDC fuera del entorno local

Configura un proveedor OIDC con el flujo de contraseña habilitado, un cliente confidencial y un usuario de pruebas. El token de acceso debe contener la audiencia library-api. Configura OIDC_ISSUER, OIDC_JWKS_URI, OIDC_AUDIENCE y, en el gateway, OIDC_TOKEN_URI para que correspondan al proveedor. En Helm, cambia el host de ejemplo library.example.com por el dominio de tu clúster. No guardes secretos reales en Git: inyecta el secreto del cliente y las credenciales mediante el gestor de secretos del entorno.

### Verificación

GitHub Actions compila y ejecuta las pruebas unitarias de los cuatro módulos, levanta la pila Docker, ejecuta la colección original de Postman y comprueba que cada servicio rechaza peticiones anónimas, tokens incorrectos y la suplantación del usuario mediante X-User-Name.

Para detener el entorno local: docker compose down. Para borrar también la base de datos local de prueba: docker compose down -v.

![GitHub Classroom Workflow](../../workflows/GitHub%20Classroom%20Workflow/badge.svg?branch=master)

## OAuth2 Authorization

### Формулировка

На базе [Лабораторной работы #4](https://github.com/bmstu-rsoi/lab2-template) реализовать OAuth2 token-based
авторизацию.

* Для авторизации использовать OpenID Connect, в роли Identity Provider использовать стороннее решение.
* На Identity Provider настроить
  использование [Resource Owner Password flow](https://auth0.com/docs/authorization/flows/resource-owner-password-flow)
  (в одном запросе передается `clientId`, `clientSecret`, `username`, `password`).
* Все методы `/api/**` (кроме `/api/v1/authorize` и `/api/v1/callback`) на всех сервисах закрыть token-based
  авторизацией.
* В качестве токена использовать [JWT](https://jwt.io/introduction), для валидации токена
  использовать [JWKs](https://auth0.com/docs/security/tokens/json-web-tokens/json-web-key-sets), _запрос к Identity
  Provider делать не нужно_.
* JWT токен пробрасывать между сервисами, при получении запроса валидацию токена так же реализовать через JWKs.
* Убрать заголовок `X-User-Name` и получать пользователя из JWT-токена.
* Если авторизация некорректная (отсутствие токена, ошибка валидации JWT токена, закончилось время жизни токена
  (поле `exp` в payload)), то отдавать 401 ошибку.
* В `scope` достаточно указывать `openid profile email`.

### Требования

1. Для автоматических прогонов тестов в файле [autograding.json](.github/classroom/autograding.json)
   и [classroom.yml](.github/workflows/classroom.yml) заменить `<variant>` на ваш вариант.
1. Код хранить на Github, для сборки использовать Github Actions.
1. Каждый сервис должен быть завернут в docker.
1. В classroom.yml дописать шаги на сборку, прогон unit-тестов.

### Пояснения

1. В роли Identity Provider можно использовать любое решение, вот несколько рабочих вариантов:
    1. [Okta](https://developer.okta.com/docs/guides/)
    2. [Auth0](https://auth0.com/developers)
2. Для получения metadata для OpenID Connect можно
   использовать [Well-Known URI](https://auth0.com/docs/security/tokens/json-web-tokens/locate-json-web-key-sets):
   `https://[base-server-url]/.well-known/openid-configuration`.
3. Из Well-Known metadata можно получить Issuer URI и JWKs URI.
4. Для реализации OAuth2 можно использовать сторонние библиотеки.

### Прием задания

1. При получении задания у вас создается fork этого репозитория для вашего пользователя.
2. После того как все тесты успешно завершатся, в Github Classroom на Dashboard будет отмечено успешное выполнение
   тестов.

### Варианты заданий

Распределение вариантов заданий аналогично [ЛР #2](https://github.com/bmstu-rsoi/lab2-template).
