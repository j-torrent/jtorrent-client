package org.jtorrent.client.metainfo;

import org.jtorrent.client.bencode.*;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Aleksei Latyshev on 16.12.2016.
 */
public class MetainfoParser {
    public final static BString ANNOUNCE = new BString("announce");
    public final static BString INFO = new BString("info");
    public final static BString PIECE_LENGTH = new BString("piece length");
    public final static BString PIECES = new BString("pieces");
    public final static BString LENGTH = new BString("length");
    public final static BString FILES = new BString("files");
    public final static BString NAME = new BString("name");
    public final static BString PATH = new BString("path");

    private static MetainfoParser ourInstance = new MetainfoParser();

    private MetainfoParser() {
    }

    public static MetainfoParser getInstance() {
        return ourInstance;
    }

    public Metainfo parse(BObject bObject) {
        BDictionary metainfoDict = BDictionary.castOrFailure(bObject);
        String announce = BString.castOrFailure(metainfoDict.getOrFailure(ANNOUNCE)).getValue();
        BDictionary infoDict = BDictionary.castOrFailure(metainfoDict.getOrFailure(INFO));
        long pieceLength = BLong.castOrFailure(infoDict.getOrFailure(PIECE_LENGTH)).getValue();
        List<String> pieces = BList.castOrFailure(infoDict.getOrFailure(PIECES)).getValue()
                .stream().map(bString -> BString.castOrFailure(bString).getValue()).collect(Collectors.toList());
        List<TorrentFileInfo> fileInfos;
        String name = BString.castOrFailure(infoDict.getOrFailure(NAME)).getValue();
        if (infoDict.getValue().containsKey(LENGTH)) {
            long length = BLong.castOrFailure(infoDict.getOrFailure(LENGTH)).getValue();
            fileInfos = Collections.singletonList(new TorrentFileInfo(length, name));
        } else {
            BList filesBList = BList.castOrFailure(infoDict.getOrFailure(FILES));
            fileInfos = filesBList.getValue().stream().map(a -> new TorrentFileInfo(
                    BLong.castOrFailure(BDictionary.castOrFailure(a).getOrFailure(LENGTH)).getValue(),
                    name + File.separator +
                            String.join(File.separator,
                                    BList.castOrFailure(BDictionary.castOrFailure(a).getOrFailure(PATH))
                                            .getValue().stream().map(x -> BString.castOrFailure(x).getValue())
                                            .collect(Collectors.toList()))
            )).collect(Collectors.toList());
        }
        return new Metainfo(announce, pieceLength, pieces, fileInfos);
    }
}
