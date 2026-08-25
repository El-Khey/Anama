package com.novelrealm.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Le pseudo est obligatoire")
        @Size(min = 3, max = 30, message = "Le pseudo doit faire entre 3 et 30 caractères")
        String pseudo,
                
        @NotBlank(message = "L'email est obligatoire") 
        @Email(message = "L'email n'est pas valide")
        String email,
                
        @NotBlank(message = "Le mot de passe est obligatoire") 
        @Size(min = 8, message = "Le mot de passe doit faire au moins 8 caractères")
        String password
        
    ) {}
    
