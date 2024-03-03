package com.example.demofido2

public class MisaIdResponse(
    val Code: Int,
    val Data: MisaIdData,
    val Success: Boolean
)

public class MisaIdData(
    val AccessToken: String,
    val TokenType: String,
    val ExpiresIn: Int,
    val RefreshToken: String,
    val User: User
)

public class User(
    val Id: String,
    val Email: String,
    val PhoneNumber: String,
    val FirstName: String,
    val LastName: String
)