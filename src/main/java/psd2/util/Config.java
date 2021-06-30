/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package psd2.util;

/**
 *
 * @author Anwar
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class Config {
    
    public Map<String , Object> DataValues;
    
    public Config() throws FileNotFoundException{
        DataValues = Data();
    }
    
    public Map<String , Object> Data() throws FileNotFoundException {
        InputStream inputStream = new FileInputStream("configuration.yml");
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(inputStream);
        return data;
    }
}
