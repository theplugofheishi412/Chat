module sn.isi.l3gl.api.chat {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;

    opens sn.isi.l3gl.api.chat to javafx.fxml;
    exports sn.isi.l3gl.api.chat;
}