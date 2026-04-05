# JWT Security Guide

## 1) First Login (Bootstrap Admin)

On first startup, if there are no users in DB, the API creates a default admin:

- username: `admin`
- password: `admin123`

You can change them using environment variables:

- `POS_BOOTSTRAP_ADMIN_USER`
- `POS_BOOTSTRAP_ADMIN_PASS`

## 2) Login and Get Token

`POST /auth/login`

```json
{
  "username": "admin",
  "password": "admin123"
}
```

Response:

```json
{
  "userId": 1,
  "username": "admin",
  "role": "ADMIN",
  "tokenType": "Bearer",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 86400000,
  "message": "Login successful"
}
```

## 3) Use Token

Send token in `Authorization` header:

`Authorization: Bearer <token>`

Example:

`GET /products`

## 4) Role Rules

- `ADMIN`:
  - Full access to all endpoints
  - Required for `POST /users`
  - Required for product create/update/delete
  - Required for category create
- `CASHIER`:
  - Can read products/categories/customers/sales
  - Can create customers and sales
  - Cannot create users
  - Cannot create/update/delete products
  - Cannot create categories

## 5) JWT Settings

Environment variables:

- `POS_JWT_SECRET` (must be at least 32 chars)
- `POS_JWT_EXPIRATION_MS` (default `86400000` = 24h)
