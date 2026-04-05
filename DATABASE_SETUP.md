# Data Storage Setup

This project supports 2 modes:
- MySQL mode (default)
- Local JSON fallback mode (optional)

Data covered:
- users/login
- inventory/products
- sales history
- customer loyalty points

## 1) Local JSON fallback mode (Optional)
Local fallback is disabled by default.

Enable it by setting:
- `POS_LOCAL_FALLBACK=true`

When enabled and MySQL is unavailable, app stores data in:
- `users.json`
- `products.json`
- `sales.json`
- `customers.json`

## 2) MySQL mode (Default)
Ensure MySQL is running (default port `3306`).

Configure connection using:
The app reads these environment variables:

- `POS_DB_URL`
- `POS_DB_USER`
- `POS_DB_PASS`

If you do not set them, defaults are:

- URL: `jdbc:mysql://localhost:3306/supermarket_pos?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- User: `root`
- Password: empty

On startup, the app auto-creates tables and inserts default users:

- `owner / 1234` (OWNER)
- `admin / admin123` (ADMIN)
- `cashier / 1111` (CASHIER)

Important:
- Change default passwords immediately in production.
