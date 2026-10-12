package dev.tessera.x;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Standalone loading/precedence/validation tests; isolated temporary files only. */
public final class ExperimentalOptionsCheck {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void reject(Path file,String text,ExperimentalOptions.Settings defaults) throws Exception {
        Files.writeString(file,text);boolean rejected=false;
        try{ExperimentalOptions.load(file,defaults);}catch(IOException expected){rejected=true;}
        check(rejected,"Invalid config accepted: "+text);check(Files.readString(file).equals(text),"Invalid file modified");
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        var empty=ExperimentalOptions.legacyDefaults(new Properties());
        Path defaults=root.resolve("defaults/tessera-x.yml");var fresh=ExperimentalOptions.load(defaults,empty);
        check(fresh.enabled() && fresh.selected().isEmpty(),"Fresh defaults enable a feature");check(Files.isRegularFile(defaults),"Defaults not created");
        check(fresh.effectiveDelayMillis()==0 && fresh.workers()==4,"Default numeric settings wrong");
        String saved=Files.readString(defaults);check(ExperimentalOptions.load(defaults,empty).equals(fresh),"Roundtrip differs");
        check(Files.readString(defaults).equals(saved),"Complete unchanged configuration rewritten");
        Properties legacy=new Properties();for(var feature:ExperimentalOptions.Feature.values())if(feature.legacyProperty!=null)legacy.setProperty(feature.legacyProperty,"true");
        legacy.setProperty("tessera-x.region-recalc-max-delay-ms","1000");legacy.setProperty("tessera-x.sensor-workers","2");
        var allLegacy=ExperimentalOptions.legacyDefaults(legacy);check(allLegacy.selected().size()==ExperimentalOptions.Feature.values().length,"Legacy mapping incomplete");
        check(allLegacy.workers()==2 && allLegacy.effectiveDelayMillis()==1000,"Legacy numeric mapping wrong");
        // An explicit false beats a legacy true for every individual feature.
        Path overrides=root.resolve("overrides.yml");StringBuilder yaml=new StringBuilder("_version: 1\nexperimental-options:\n  enabled: true\n  sensor-workers: 0\n  region-recalc-max-delay-ms: 2000\n");
        for(var feature:ExperimentalOptions.Feature.values())yaml.append("  ").append(feature.key).append(": false\n");
        Files.writeString(overrides,yaml);var disabled=ExperimentalOptions.load(overrides,allLegacy);
        for(var feature:ExperimentalOptions.Feature.values())check(!disabled.isEnabled(feature),"False ignored for "+feature);
        check(disabled.workers()==0 && disabled.effectiveDelayMillis()==0,"Explicit numeric/region disable ignored");
        Path kill=root.resolve("kill.yml");Files.writeString(kill,"experimental-options:\n  enabled: false\n");var killed=ExperimentalOptions.load(kill,allLegacy);
        for(var feature:ExperimentalOptions.Feature.values())check(!killed.isEnabled(feature),"Master disable ignored for "+feature);
        check(killed.effectiveDelayMillis()==0,"Master did not disable region deadline");
        Path partial=root.resolve("partial.yml");Files.writeString(partial,"custom-entry: preserved\nexperimental-options:\n  poi-scratch: true\n");
        var partialSettings=ExperimentalOptions.load(partial,empty);check(partialSettings.isEnabled(ExperimentalOptions.Feature.POI_SCRATCH),"Partial positive setting ignored");
        check(Files.readString(partial).contains("custom-entry: preserved"),"Unknown entry discarded");check(partialSettings.selected().size()==1,"Partial config enabled other features");
        Path invalid=root.resolve("invalid.yml");
        for(String text:List.of("_version: 2\n","experimental-options: []\n","experimental-options:\n  poi-scratch: potato\n",
            "experimental-options:\n  sensor-workers: 9\n","experimental-options:\n  sensor-workers: 1.5\n",
            "experimental-options:\n  region-recalc-max-delay-ms: 99\n","experimental-options:\n  region-recalc-max-delay-ms: 60001\n",
            "experimental-options: [broken\n"))reject(invalid,text,empty);
        legacy.setProperty("tessera-x.sensor-workers","2147483648");check(ExperimentalOptions.legacyDefaults(legacy).workers()==4,"Legacy Integer fallback changed");
        legacy.setProperty("tessera-x.sensor-workers","-1");check(ExperimentalOptions.legacyDefaults(legacy).workers()==0,"Legacy worker lower clamp changed");
        Path startup=root.resolve("startup.yml");Files.writeString(startup,"experimental-options:\n  poi-scratch: true\n");ExperimentalOptions.initialize(startup);
        check(ExperimentalOptions.enabled(ExperimentalOptions.Feature.POI_SCRATCH),"Startup snapshot not published");
        Files.writeString(startup,"experimental-options:\n  poi-scratch: false\n");check(ExperimentalOptions.enabled(ExperimentalOptions.Feature.POI_SCRATCH),"Config changed without restart");
        boolean reloadRejected=false;try{ExperimentalOptions.initialize(startup);}catch(IllegalStateException expected){reloadRejected=true;}
        check(reloadRejected,"Reload accepted after feature access");
        Files.writeString(root.resolve("result.json"),"{\"status\":\"PASS\",\"assertions\":"+assertions+"}");System.out.println("PASS: "+assertions+" configuration assertions");
    }
}
