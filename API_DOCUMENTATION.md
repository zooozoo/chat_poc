# API Documentation

This document describes the REST API interfaces for the Chat POC application.

## Base URL
- Local Development: `http://localhost:8080`

## Common Response Format
All API responses follow a standard format:

```json
{
  "success": true,      // true or false
  "data": { ... },      // Payload (null if error or no data)
  "message": null       // Error message (null if success)
}
```

---

## Authentication

### Login (Admin)
- **URL**: `/api/admins/login`
- **Method**: `POST`
- **Summary**: Authenticates an admin and returns a JWT token.

#### Request Body
| Field | Type | Description | Required |
| :--- | :--- | :--- | :--- |
| `email` | `String` | Admin email address | Yes |

```json
{
  "email": "admin@example.com"
}
```

#### Response Body (`LoginResponse`)
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "admin@example.com",
    "userType": "ADMIN",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  },
  "message": null
}
```

### Login (User)
- **URL**: `/api/users/login`
- **Method**: `POST`
- **Summary**: Authenticates a user (creates one if not exists) and returns a JWT token.

#### Request Body
| Field | Type | Description | Required |
| :--- | :--- | :--- | :--- |
| `email` | `String` | User email address | Yes |

```json
{
  "email": "user@example.com"
}
```

#### Response Body (`LoginResponse`)
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com",
    "userType": "USER",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  },
  "message": null
}
```

---

## Admin API
**Authorization**: Bearer Token (Admin)

### Get Current Admin Info
- **URL**: `/api/admins/me`
- **Method**: `GET`
- **Summary**: Retrieves profile information of the currently logged-in admin.

#### Response Body (`AdminResponse`)
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "admin@example.com",
    "name": "Manager Kim",
    "createdAt": "2024-01-01 10:00:00"
  },
  "message": null
}
```

### Get Unassigned Chat Rooms
- **URL**: `/api/admins/chatrooms/unassigned`
- **Method**: `GET`
- **Summary**: Retrieves a list of chat rooms that have not yet been assigned to any admin.

#### Response Body (`ChatRoomListResponse`)
```json
{
  "success": true,
  "data": {
    "chatRooms": [
      {
        "id": 10,
        "userId": 5,
        "userEmail": "user@example.com",
        "unreadCount": 2,
        "lastMessageContent": "Help me",
        "lastMessageAt": "2024-01-01 12:00:00",
        "assignedAdminEmail": null,
        "createdAt": "2024-01-01 11:00:00"
      }
    ]
  },
  "message": null
}
```

### Get My Assigned Chat Rooms
- **URL**: `/api/admins/chatrooms/mine`
- **Method**: `GET`
- **Summary**: Retrieves a list of chat rooms assigned to the current admin.

#### Response Body (`ChatRoomListResponse`)
```json
{
  "success": true,
  "data": {
    "chatRooms": [
      {
        "id": 11,
        "userId": 6,
        "userEmail": "customer@example.com",
        "unreadCount": 0,
        "lastMessageContent": "Thank you",
        "lastMessageAt": "2024-01-01 12:30:00",
        "assignedAdminEmail": "admin@example.com", // Current Admin
        "createdAt": "2024-01-01 11:30:00"
      }
    ]
  },
  "message": null
}
```

### Get All Chat Rooms
- **URL**: `/api/admins/chatrooms`
- **Method**: `GET`
- **Summary**: Retrieves all chat rooms (for monitoring).

#### Response Body (`ChatRoomListResponse`)
- Same structure as "Get Unassigned Chat Rooms" but includes all rooms.

### Assign Chat Room
- **URL**: `/api/admins/chatrooms/{id}/assign`
- **Method**: `POST`
- **Summary**: Assigns a specific chat room to the current admin.

#### Path Parameters
| Parameter | Type | Description |
| :--- | :--- | :--- |
| `id` | `Long` | Chat Room ID |

#### Response Body
```json
{
  "success": true,
  "data": null,
  "message": null
}
```

---

## User API
**Authorization**: Bearer Token (User)

### Get Current User Info
- **URL**: `/api/users/me`
- **Method**: `GET`
- **Summary**: Retrieves profile information of the currently logged-in user.

#### Response Body (`UserResponse`)
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com",
    "createdAt": "2024-01-01 09:00:00"
  },
  "message": null
}
```

### Get My Chat Room
- **URL**: `/api/users/chatroom`
- **Method**: `GET`
- **Summary**: Retrieves the chat room for the current user. If one does not exist, it is automatically created.

#### Response Body (`ChatRoomResponse`)
```json
{
  "success": true,
  "data": {
    "id": 10,
    "userEmail": "user@example.com",
    "unreadCount": 0,
    "lastMessageContent": "Hello",
    "lastMessageAt": "2024-01-01 12:00:00",
    "createdAt": "2024-01-01 11:00:00"
  },
  "message": null
}
```

---

## Shared API
**Authorization**: Bearer Token (Admin or User)

### Enter Chat Room
- **URL**: `/api/chatrooms/{id}`
- **Method**: `GET`
- **Summary**: Retrieves detailed information about a chat room, including the message history preview. Access is restricted to the room owner (User) or an Admin.
- **Side Effect**: Marks the room as "read" for the entering user.

#### Path Parameters
| Parameter | Type | Description |
| :--- | :--- | :--- |
| `id` | `Long` | Chat Room ID |

#### Response Body (`ChatRoomDetailResponse`)
```json
{
  "success": true,
  "data": {
    "id": 10,
    "userEmail": "user@example.com",
    "assignedAdminEmail": "admin@example.com",
    "createdAt": "2024-01-01 11:00:00",
    "messages": [
      {
        "id": 100,
        "senderId": 5,
        "senderType": "USER",
        "content": "Hello",
        "isRead": true,
        "readAt": "2024-01-01 12:05:00",
        "createdAt": "2024-01-01 12:00:00"
      }
    ]
  },
  "message": null
}
```

### Get Messages
- **URL**: `/api/chatrooms/{id}/messages`
- **Method**: `GET`
- **Summary**: Retrieves paginated messages for a specific chat room.

#### Path Parameters
| Parameter | Type | Description |
| :--- | :--- | :--- |
| `id` | `Long` | Chat Room ID |

#### Query Parameters
| Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `page` | `Int` | `0` | Page number (0-indexed) |
| `size` | `Int` | `20` | Number of items per page |

#### Response Body (`MessageListResponse`)
```json
{
  "success": true,
  "data": {
    "messages": [
      {
        "id": 100,
        "senderId": 5,
        "senderType": "USER",
        "content": "Hello",
        "isRead": true,
        "readAt": "2024-01-01 12:05:00",
        "createdAt": "2024-01-01 12:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  },
  "message": null
}
```
