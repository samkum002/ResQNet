package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserInfoDto {
    private ObjectId userId;
    private String username;
    private List<String> roles;

}
