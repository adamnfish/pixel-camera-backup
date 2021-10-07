Photonamez
==========

Rename photos from a Pixel phone to make it easier to sync them to
cloud storage.

## Usage

Get your photos off the phone. You can use `adb` to do this using the CLI.

    ./adb pull /sdcard/DCIM/Camera/ <photo-dir>

Run this program to move the photos into a more convenient directory
structure.

    $ sbt run <photo-dir> <output-dir>

`<photo-dir>` is the place where all your photos are stored.

`<output-dir>` is where the new directory structure should be written.

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

    aws s3 sync ~/Pictures/phone-backup/sync s3://my-storage/phone/
