package tr.gov.ibb.envision;

import java.util.ArrayList;
import java.util.List;

/**
 * Sistemdeki environment variable'ları okur.
 */
public class EnvReader {

    /** OS env variable'larını EnvVariable listesi olarak döner. */
    public List<EnvVariable> readAll() {
        List<EnvVariable> result = new ArrayList<>();
        System.getenv().forEach((k, v) -> result.add(EnvVariable.of(k, v)));
        return result;
    }
}
