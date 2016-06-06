var browserify = require('browserify')(),
    fs = require('fs'),
    inFile = process.argv[2],
    outFile = process.argv[3],
    out = fs.createWriteStream(outFile);

browserify.add(inFile);
browserify.bundle().pipe(out);
