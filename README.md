# SuperMarketPOS

Smart supermarket point-of-sale system built with Java.

![SuperMarketPOS Logo](src/main/resources/images/abusamir-logo-circle.png)

[![Java](https://img.shields.io/badge/Java-17-blue)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-orange)](https://openjfx.io/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1)](https://www.mysql.com/)

## Overview

SuperMarketPOS combines a modern JavaFX desktop app with a Spring Boot REST API for day-to-day supermarket operations:

- Fast POS checkout
- Inventory and barcode lookup
- Customer loyalty points
- Sales tracking and returns
- Accounting reports (trial balance, P&L, balance sheet)
- Branch and workforce management
- Role-based access with JWT authentication

## Key Features

- Desktop POS interface for cashier, admin, and owner workflows
- Product and category management
- Barcode search and invoice flow
- Sales history with cancellation and return handling
- Customer points add/redeem endpoints
- Accounting journals and financial reporting endpoints
- Attendance and payroll summary endpoints
- Arabic/English localization support

## Architecture

- Desktop: JavaFX (`com.pos.Main`)
- API: Spring Boot (`com.pos.api.PosApiApplication`)
- Combined launcher: `com.pos.DesktopWithApiLauncher` (starts API + desktop together when API sync is enabled)
- Storage: MySQL by default, with optional local JSON fallback in desktop mode when DB is unavailable

## Tech Stack

- Java 17
- JavaFX 21
- Spring Boot 3.2.5
- Spring Security + JWT
- Spring Data JPA
- MySQL Connector/J
- Maven

## Quick Start

### Prerequisites

- JDK 17+
- Maven 3.9+
- MySQL 8+ (recommended)

### 1) Run Desktop + API (Recommended)

```bash
mvn -DskipTests clean package
java -jar target/SuperMarketPOS-1.0-desktop.jar
```

### 2) Run API Only

```bash
mvn spring-boot:run
```

Default API URL: `http://127.0.0.1:8080`

### 3) Run Desktop Only (No API Sync)

PowerShell example:

```powershell
$env:POS_API_SYNC="false"
$env:POS_LOCAL_FALLBACK="true"
mvn javafx:run
```

## Default Development Accounts

Use these only for local development, then change passwords immediately:

- `owner / 1234` (OWNER)
- `admin / admin123` (ADMIN)
- `cashier / 1111` (CASHIER)

## Environment Variables

- `POS_API_SYNC` (default `true`)
- `POS_API_BASE_URL` (default `http://127.0.0.1:8080`)
- `POS_API_PORT` (default `8080`)
- `POS_DB_URL`
- `POS_DB_USER`
- `POS_DB_PASS`
- `POS_LOCAL_FALLBACK` (default `false`, desktop mode)
- `POS_JWT_SECRET` (must be at least 32 chars)
- `POS_JWT_EXPIRATION_MS` (default `86400000`)
- `POS_BOOTSTRAP_ADMIN_USER` / `POS_BOOTSTRAP_ADMIN_PASS`
- `POS_BOOTSTRAP_OWNER_USER` / `POS_BOOTSTRAP_OWNER_PASS`

## API Authentication (JWT)

Login endpoint:

```http
POST /auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "admin123"
}
```

Then call secured endpoints with:

```http
Authorization: Bearer <token>
```

## Main API Areas

- `/auth`, `/users`
- `/products`, `/categories`
- `/customers`, `/sales`
- `/branches`
- `/accounts`, `/journal-entries`
- `/employees`, `/attendance`, `/payroll`

## Build Installer (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File .\build-installer.ps1
```

This script builds:

- `.exe` installer (if WiX tools are available), or
- portable app-image + ZIP package

## Security Notes

- Never commit real secrets (DB passwords, JWT secrets, API keys).
- Rotate any credentials that were exposed in public history.
- Keep `POS_JWT_SECRET` strong and private in production.

## Contributing

Issues and pull requests are welcome.
