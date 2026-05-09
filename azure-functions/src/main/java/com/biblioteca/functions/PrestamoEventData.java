package com.biblioteca.functions;

public class PrestamoEventData {

    private final String tituloLibro;
    private final int idUsuario;

    public PrestamoEventData(String tituloLibro, int idUsuario) {
        this.tituloLibro = tituloLibro;
        this.idUsuario = idUsuario;
    }

    public String getTituloLibro() {
        return tituloLibro;
    }

    public int getIdUsuario() {
        return idUsuario;
    }
}
