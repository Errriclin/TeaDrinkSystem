package com.teadrink.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class ProductRecipeSaveRequest {
    @NotNull
    @Valid
    private List<ProductRecipeItemRequest> items;
}
