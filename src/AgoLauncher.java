import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

public class AgoLauncher extends Application {
    public static Properties config;
    public static final Path CONFIG_PATH;

    static {
        String localAppDataDir = System.getenv("LOCALAPPDATA");
        if (localAppDataDir != null) {
            Path configDir = Paths.get(localAppDataDir, "AgoLauncher");
            try {
                Files.createDirectories(configDir);
                System.out.println("設定フォルダ: " + configDir);
            } catch (IOException e) {
                System.err.println("AgoLauncherフォルダの作成に失敗しました。カレントディレクトリを使用します。");
                configDir = Paths.get("");
            }
            CONFIG_PATH = configDir.resolve("agolauncher.properties");
        } else {
            System.out.println("LocalAppDataのパスの取得に失敗しました。カレントディレクトリを使用します。");
            CONFIG_PATH = Paths.get("agolauncher.properties");
        }
    }


    private static final String DEFAULT_INSTALL_PATH = System.getProperty("user.home") + "\\Desktop";

    public static Label modpackVersion;

    @Override
    public void start(Stage primaryStage) {
        TextField installPathField = new TextField(config.getProperty("install_path"));
        installPathField.setPrefWidth(250);
        HBox.setHgrow(installPathField, Priority.ALWAYS);
        TextField modpackURL = new TextField(config.getProperty("modpack_url"));
        modpackURL.setPrefWidth(250);
        HBox.setHgrow(modpackURL, Priority.ALWAYS);

        Button selectDirButton = new Button("...");
        selectDirButton.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("ゲームディレクトリを選択");

            directoryChooser.setInitialDirectory(new File(installPathField.getText()));
            File selectedDirectory = directoryChooser.showDialog(primaryStage);

            if (selectedDirectory != null) {
                installPathField.setText(selectedDirectory.getAbsolutePath());
                saveConfig("install_path", selectedDirectory.getAbsolutePath());
            }
        });
        HBox.setHgrow(installPathField, Priority.ALWAYS);
        HBox pathBox = new HBox(5, installPathField, selectDirButton);

        Button updateButton = new Button("Modpackを更新する");
        Button playButton = new Button("マインクラフトを起動");

        updateButton.setOnAction(e -> updateModPack(installPathField.getText(), modpackURL.getText()));
        playButton.setOnAction(e -> launchMinecraft());

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setVgap(10);
        grid.setHgap(10);

        Label directoryLabel = new Label("ゲームディレクトリ");
        GridPane.setHalignment(directoryLabel, HPos.RIGHT);
        grid.add(directoryLabel, 0, 0);
        grid.add(pathBox, 1, 0);

        Label urlLabel = new Label("Modpack-json");
        GridPane.setHalignment(urlLabel, HPos.RIGHT);
        grid.add(urlLabel, 0, 1);
        grid.add(modpackURL, 1, 1);

        Label modpackLabel = new Label("現在のModpack");
        GridPane.setHalignment(modpackLabel, HPos.RIGHT);
        grid.add(modpackLabel, 0, 2);
        modpackVersion = new Label(config.getProperty("modpack_version"));
        grid.add(modpackVersion, 1, 2);

        VBox layout = new VBox(10, grid, updateButton, playButton);
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 432, 221);
        primaryStage.getIcons().add(new Image("file:icon.png"));
        primaryStage.setTitle("AgoLauncher");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void downloadFileFromURL(String fileURL, Path savePath) throws IOException{
        try (InputStream in = new URL(fileURL).openStream()) {
            Files.copy(in, savePath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Download complete: " + savePath);
        } catch (IOException e) {
            System.err.println("Download failed: " + e.getMessage());
            throw e;
        }
    }

    public static void updateModPack(String dir, String url) {
        saveConfig("install_path", dir);
        saveConfig("modpack_url", url);

        ProgressWindow progressWindow = new ProgressWindow();
        progressWindow.show();

        new Thread(() -> {
            try {
                progressWindow.updateStatus("Modpack JSONをダウンロード中...", 0.2);
                Path modpackJsonPath = Paths.get("agolauncher_modpack.json");
                downloadFileFromURL(url, modpackJsonPath);

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode modpackJson = objectMapper.readTree(new File("agolauncher_modpack.json"));

                String modpackversion = modpackJson.get("name").asText() + "_" + modpackJson.get("version").asText();
                String old_modpackversion = config.getProperty("modpack_version");

                if (!Objects.equals(old_modpackversion, modpackversion)) {
                    progressWindow.updateStatus("古いModpackを削除中...", 0.4);
                    File modsDir = new File(dir, "mods");
                    if (!modsDir.exists()) {
                        modsDir.mkdir();
                    }

                    String del_command = String.format("powershell.exe -Command Remove-Item -Path \"%s\\*\" -Recurse -Force", modsDir.getPath());
                    Process process = Runtime.getRuntime().exec(del_command);
                    process.waitFor();

                    JsonNode modsArray = modpackJson.get("mods");
                    if (modsArray != null) {
                        int totalMods = modsArray.size();
                        int count = 0;

                        for (JsonNode mod : modsArray) {
                            count++;
                            String modName = mod.get("modname").asText();
                            String modUrl = mod.get("url").asText();

                            progressWindow.updateStatus("ダウンロード中: " + modName, 0.5 + (0.5 * count / totalMods));
                            Path savePath = Paths.get(modsDir.getPath(), modName);
                            downloadFileFromURL(modUrl, savePath);
                        }
                    }

                    Platform.runLater(() -> modpackVersion.setText(modpackversion));
                    saveConfig("modpack_version", modpackversion);
                }

                Files.deleteIfExists(modpackJsonPath);

                progressWindow.updateStatus("更新完了！", 1.0);
                Platform.runLater(() -> {
                    AlertBox.display("更新完了", "Modpackの更新が完了しました。");
                    progressWindow.closeWindow();
                });

            } catch (IOException | InterruptedException e) {
                System.err.println("Modpackの更新に失敗しました: " + e.getMessage());
                Platform.runLater(() -> AlertBox.display("エラー", "Modpackの更新に失敗しました。\n" + e.getMessage()));
                progressWindow.closeWindow();
            }
        }).start();
    }

    public static void launchMinecraft() {
        try {
            Runtime.getRuntime().exec("powershell.exe Start-Process \"explorer.exe\" \"shell:appsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft\"");
            System.exit(0);
        } catch (IOException e) {
            System.err.println("MinecraftLauncher.exe を起動できません: " + e.getMessage());
        }
    }

    public static Properties loadConfig() {
        Properties prop = new Properties();

        if (Files.notExists(CONFIG_PATH)) {
            createDefaultConfig();
        }

        try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
            prop.load(input);
            System.out.println("設定ファイルを読み込みました。");
        } catch (IOException e) {
            System.err.println("設定ファイルの読み込みに失敗しました: " + e.getMessage());
        }

        return prop;
    }

    public static void saveConfig(String key, String value) {
        Properties prop = loadConfig();
        prop.setProperty(key, value);

        try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
            prop.store(output, "AgoLauncher Configuration");
            System.out.println("設定を更新しました: " + key + " = " + value);
        } catch (IOException e) {
            System.err.println("設定ファイルの更新に失敗しました: " + e.getMessage());
        }
    }

    public static void createDefaultConfig() {
        Properties prop = new Properties();
        prop.setProperty("install_path", DEFAULT_INSTALL_PATH);
        prop.setProperty("modpack_url", "");
        prop.setProperty("modpack_version", "");

        try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
            prop.store(output, "AgoLauncher Configuration");
            System.out.println("デフォルト設定ファイルを作成しました。");
        } catch (IOException e) {
            System.err.println("設定ファイルの作成に失敗しました: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        config = loadConfig();
        launch(args);
    }
}

