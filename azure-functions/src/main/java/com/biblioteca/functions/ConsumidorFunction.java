package com.biblioteca.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

public class ConsumidorFunction {

    @FunctionName("ConsumidorFunction")
    public void run(
        //Gatillador de Azure Event Grid
        @EventGridTrigger(name = "eventGridEvent") String message,
        final ExecutionContext context
    ) {
        context.getLogger().info("Evento recibido desde Azure Event Grid: " + message);
    }
}
