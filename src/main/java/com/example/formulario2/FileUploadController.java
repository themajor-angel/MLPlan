package com.example.formulario2;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;


import com.example.formulario2.storage.StorageFileNotFoundException;
import com.example.formulario2.storage.StorageService;

import static java.util.UUID.randomUUID;

@Controller
public class FileUploadController {

    private final StorageService storageService;
    private List<String> logs = null;

    @Autowired
    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/")
    public String listUploadedFiles(Model model) throws IOException {

        model.addAttribute("files", storageService.loadAll().map(
                path -> MvcUriComponentsBuilder.fromMethodName(FileUploadController.class,
                        "serveFile", path.getFileName().toString()).build().toUri().toString())
                .collect(Collectors.toList()));

        return "uploadForm";
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    }

    @GetMapping("/logs/{startLine}")
    @ResponseBody
    public List<String> getLogs(@PathVariable int startLine) {
        return logs.subList(startLine, logs.size());
    }



    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes,
                                   @RequestParam("timeout")  int time) {

        storageService.store(file);
        Resource resource = storageService.loadAsResource(file.getOriginalFilename());

        UUID serialversionUID = randomUUID();
        /*Path donde se guardan los objetos serializados*/
        String path = "./mods/";
        String fileName = path + serialversionUID + ".mod";

        Thread thread = new Thread(() -> {
            try {
                File datasetFile = resource.getFile();
                String pathToFile = datasetFile.getPath();

                File serializedFile = new File(fileName);
                serializedFile.createNewFile();

                createClassifier(pathToFile, time, fileName, serialversionUID);
            }
            catch(Exception e) {
                System.out.println("An error was thrown while attempting to upload a dataset:");
                e.printStackTrace();
            }
        });
        thread.start();

//        redirectAttributes.addFlashAttribute("message",
//                "You successfully uploaded " + file.getOriginalFilename() + "!");

        return "redirect:/progress";
    }

    // TODO: Accept a uuid to filter logs for a specific process
    @GetMapping("/progress")
    public String progress() {
        return "progress";
    }

    @RequestMapping(value="/predict", method=RequestMethod.POST, params="action=predict")
    public String predict() {
        return "predict";
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

    public void createClassifier(String dataFile, int timeout, String fileName, UUID id) throws IOException, InterruptedException {

        /*Path al Jar*/
        File jar = new File("./mlplan-cli-0.2.4.jar");
        /*This yields the full system path (with the file: protocol) for Log4J configuration*/
        String fullConfigUrl = ClassLoader.getSystemResource("./conf/log4j.xml").toString();
        String logConfigLevel = "-D" + "\"" + "log4j.info" + "\"";
        String logConfigFile = "-D" + "\"" + "log4j.configuration=" + fullConfigUrl + "\"";
        /*Path  donde guarda los mensajes de consola de cada archivo serializado*/
        String pathToLogOutFile = "./upload-docs/logs/" + id + "-OUT.txt";
        String pathToLogErrFile = "./upload-docs/logs/" + id + "-ERROR.txt";
        File logOutputFile = new File(pathToLogOutFile);
        File logErrorFile = new File(pathToLogErrFile);
        String dataFileCom = "\"" + dataFile + "\"";
        String fileNameCom = "\"" + fileName + "\"";
        String jarCom = "\"" + jar.getAbsolutePath() + "\"";

        /*Argumentos para correr el jar desde consola*/
        List<String> args = Arrays.asList("java", logConfigLevel, logConfigFile, "-cp", jarCom, "ai.libs.mlplan.cli.MLPlanCLI", "-f", dataFileCom, "-t", String.valueOf(timeout), "-om", fileNameCom);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectOutput(logOutputFile).redirectError(logErrorFile);
        pb.directory(new File("./upload-docs"));

        // TODO: assign uuid to file and return that and use it as an argument for /progress when redirecting
        File outputFile = new File("./upload-docs/logs/joe-mama.log");
        outputFile.createNewFile();

        /* use (in both) ProcessBuilder.Redirect.INHERIT for getting logs on console*/
        pb.redirectOutput(outputFile);
        pb.redirectError(outputFile);
        Process p = pb.start();

        // update in-memory logs every second
        while (p.isAlive()) {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(outputFile));
            logs = bufferedReader.lines().collect(Collectors.toList());
            //System.out.println(logs.size());
            Thread.sleep(1000);
        }
    }
}