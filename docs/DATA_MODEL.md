# Data Model

## HostProfile

- id: String
- name: String
- hostname: String
- port: Int
- username: String
- authType: PASSWORD / KEY
- keyRef: String?
- lastConnectedAt: Long?

## Shortcut

- id: String
- scope: GLOBAL / HOST
- hostId: String?
- name: String
- command: String
- addNewline: Boolean
- sortOrder: Int

## AppSettings

- theme
- fontSize
- fontFamily
