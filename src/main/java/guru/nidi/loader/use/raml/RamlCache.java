/*
 * Copyright © 2015 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.loader.use.raml;

import guru.nidi.loader.Loader;
import guru.nidi.loader.LoadingException;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;

import java.io.*;

public class RamlCache {
    private static final File CACHE_DIR = new File(System.getProperty("java.io.tmpdir"), "bamls");

    static {
        CACHE_DIR.mkdirs();
    }

    private final Loader loader;

    public RamlCache(Loader loader) {
        this.loader = loader;
    }

    //TODO save all included ramls of a raml. when any of them changes, reparse!
    public RamlModelResult loadRaml(String name) {
        final File file = new File(CACHE_DIR, escaped(loader.config()) + "-" + escaped(simpleName(name)) + ".braml");
        if (!file.exists()) {
            return parseAndSave(loader, name, file);
        }
        final InputStream in = loader.fetchResource(name, file.lastModified());
        if (in != null) {
            final byte[] data = loadedData(name, in);
            return parseAndSave(new PreloadedLoader(loader, name, data), name, file);
        }
        try {
            return load(file);
        } catch (IOException | ClassNotFoundException e) {
            return parseAndSave(loader, name, file);
        }
    }

    private String simpleName(String name) {
        final int user = name.indexOf('@');
        final int suffix = name.lastIndexOf('.');
        return name.substring(user + 1, suffix < 0 ? name.length() : suffix);
    }

    private RamlModelResult parseAndSave(Loader loader, String name, File file) {
        final RamlModelResult raml = new RamlModelBuilder(new LoaderRamlResourceLoader(loader)).buildApi(name);
        save(raml, file);
        return raml;
    }

    private void save(RamlModelResult raml, File file) {
        try (final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(raml);
        } catch (IOException e) {
            //ignore
        }
    }

    private RamlModelResult load(File file) throws IOException, ClassNotFoundException {
        try (final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (RamlModelResult) ois.readObject();
        }
    }

    private String escaped(String name) {
        final StringBuilder s = new StringBuilder(name);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if ((c < '0' || c > '9') && (c < 'A' || c > 'Z') && (c < 'a' || c > 'z')) {
                s.setCharAt(i, '-');
            }
        }
        return s.toString();
    }

    private byte[] loadedData(String name, InputStream ramlInputStream) {
        try (final InputStream in = ramlInputStream;
             final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final byte[] buf = new byte[1000];
            int read;
            while ((read = in.read(buf)) > 0) {
                baos.write(buf, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new LoadingException("Could not read resource '" + name + "'", e);
        }
    }

    private static class PreloadedLoader implements Loader {
        private final Loader loader;
        private final String name;
        private final byte[] data;

        public PreloadedLoader(Loader loader, String name, byte[] data) {
            this.loader = loader;
            this.name = name;
            this.data = data;
        }

        @Override
        public InputStream fetchResource(String name, long ifModifiedSince) {
            if (name.equals(this.name)) {
                return new ByteArrayInputStream(data);
            }
            return loader.fetchResource(name, ifModifiedSince);
        }

        @Override
        public String config() {
            return loader.config();
        }
    }
}
