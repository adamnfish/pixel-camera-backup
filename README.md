Pixel camera backup
===================

Reorganise photos from a Pixel phone to make it easier to sync them
to cloud storage.

Before:
```
photos
├── IMG_20200911_135149.jpg
├── IMG_20200911_207135.jpg
├── IMG_20200920_135555
│   ├── 00000PORTRAIT_00000_BURST20200920135555741.jpg
│   └── 00100trPORTRAIT_00100_BURST20200920135555741_COVER.jpg
├── PXL_20210921_185038494.PORTRAIT.jpg
├── PXL_20210925_132726144.LS.mp4
├── PXL_20211001_132410554.jpg
└── VID_20200813_191831_LS.mp4
```

After:
```
photos/
├── 2020
│   ├── 08
│   │   └── 13
│   │       └── VID_20200813_191831_LS.mp4
│   └── 09
│       ├── 11
│       │   ├── IMG_20200911_135149.jpg
│       │   └── IMG_20200911_207135.jpg
│       └── 20
│           └── IMG_20200920_135555
│               ├── 00000PORTRAIT_00000_BURST20200920135555741.jpg
│               └── 00100trPORTRAIT_00100_BURST20200920135555741_COVER.jpg
└── 2021
    ├── 09
    │   ├── 21
    │   │   └── PXL_20210921_185038494.PORTRAIT.jpg
    │   └── 25
    │       └── PXL_20210925_132726144.LS.mp4
    └── 10
        └── 01
            └── PXL_20211001_132410554.jpg

```

The photos are sorted into subdirectories by year, month and day.

It is challenging to manage a single directory containing thousands
of photos, especially using the Web UIs offered by popular Cloud Storage
solutions. `pixel-camera-backup` is here to help.

## Usage

Get your photos off the phone. You can use `adb` to do this using the CLI.

    ./adb pull /sdcard/DCIM/Camera/ <photo-dir>

Run this program to move the photos into a more convenient directory
structure.

    sbt run <photo-dir> <output-dir> [--commit]

`<photo-dir>` is the place where all your photos are stored.

`<output-dir>` is where the new directory structure should be written.

By default, the program will run do a dry-run. It performs all its
checks without actually copying any files. Once you're happy with the
output and have resolved any conflicts, you can tell the program to
actually copy the files into the new structure.

`--commit` once you've cleared up any conflicts, add this flag to run for real

### Example

    # from your android platform-tools directory
    ./adb pull /sdcard/DCIM/Camera/ ~/Pictures/phone-backup/

    # from this repository's directory
    sbt run ~/Pictures/phone-backup/Camera ~/Pictures/phone-backup/sync

This will copy the photo files off your phone and onto your computer
in the `Pictures/phone-backup` directory. This copies the `Camera`
directory and all its contents.

We then provide the `Pictures/phone-backup/Camera` directory as the
input to this script. The resulting tree of files will be written to
`~/Pictures/phone-backup/sync`.

You can then use your cloud provider of choice's tool for synchronising
the contents of this directory to the cloud.

e.g. to backup your photos into Amazon S3, you might use the following
command.

    aws s3 sync ~/Pictures/phone-backup/sync/ s3://my-storage/phone/
