# Actualización de dependencias detectadas por Snyk

Este ZIP contiene solo 15 archivos relativos a la raíz de `flashstock-microservices`.

- Los cuatro servicios Java se actualizan de Spring Boot 3.3.5 a 4.1.1 para incorporar Spring Framework 7 y las correcciones que una actualización parcial a Boot 3 no incluye.
- Se actualizan los starters de MVC y OAuth2, springdoc a 3.1.1 y Resilience4j a la edición de Boot 4 (2.4.0).
- Las versiones administradas se ajustan a Tomcat 11.0.25, Jackson 3.1.7 y Jackson 2.21.7. `orden` y `shipping` conservan su código Jackson 2 mediante `spring-boot-jackson2`.
- Ocho pruebas usan las anotaciones que movió Spring Boot 4; tres configuraciones retiran el matcher obsoleto de logout GET. El logout local queda en el POST predeterminado con CSRF; el logout del BFF (`/auth/logout`) es independiente.

## Aplicación y verificación

Desde la raíz del repositorio, sin borrar archivos existentes:

```bash
unzip -o FlashStock-parche-Snyk-20261002.zip -d .
set -e
for service in auth inventory orden shipping; do
  (cd "$service" && ./mvnw -B clean test)
done
snyk test --all-projects --maven-skip-wrapper --severity-threshold=high
```

Este entorno no permite descargar artefactos nuevos de Maven Central; se verificó la estructura de los POM, pero no fue posible ejecutar las pruebas ni Snyk con el árbol actualizado. Ejecuta los comandos antes de construir imágenes o desplegar. Si la compilación falla por otra incompatibilidad de la migración, comparte el primer error completo de Maven para corregirlo antes de publicar.
