import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProgressWindow {
    private Stage window;
    private Label statusLabel;
    private ProgressBar progressBar;

    public ProgressWindow() {
        window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setTitle("Modpack 更新中");
        window.getIcons().add(new Image("file:icon.png"));
        window.setResizable(false);

        statusLabel = new Label("開始中...");
        progressBar = new ProgressBar(0);

        VBox layout = new VBox(10);
        layout.getChildren().addAll(statusLabel, progressBar);
        layout.setStyle("-fx-padding: 10; -fx-alignment: center;");

        Scene scene = new Scene(layout, 350, 200);
        window.setScene(scene);
    }

    public void show() {
        Platform.runLater(window::show);
    }

    public void closeWindow() {
        Platform.runLater(window::close);
    }

    public void updateStatus(String message, double progress) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            progressBar.setProgress(progress);
        });
    }
}