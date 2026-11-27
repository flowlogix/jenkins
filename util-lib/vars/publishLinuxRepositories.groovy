// Publishes the Linux packages that native-build.yml built into <dir>/deb and <dir>/rpm as an APT and
// a DNF repository on the hosting provider: deb/<name> and rpm/<name> under downloadsDir, each listing
// only the release being published. Signs the RPMs and both repository indexes with the Flow Logix key,
// and indexes the RPMs with createrepo_c in a container. The container gets the RPMs and returns the
// index through tar on stdin and stdout, so it works with a remote Docker daemon.

void call(String name, String dir = 'dist') {
    String key = 'Flow Logix, Inc.'
    String host = websiteHost()
    String remote = downloadsDir()
    String url = downloadsUrl()

    mavenSettingsCredentials false, {
        withEnv(["GPG_KEY=$key", "DIR=$dir", "NAME=$name"]) {
            sh '''
            # A failure anywhere in a pipe, such as docker run, fails the step
            set -o pipefail
            # Like maven-gpg-plugin, gpg reads the passphrase from a pipe, never from a file
            sign() {
                printf '%s' "$MAVEN_GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback \
                    --passphrase-fd 0 --local-user "$GPG_KEY" "$@"
            }

            # rpmsign feeds gpg the package on stdin, so the passphrase comes on file descriptor 3.
            # One RPM at a time, so each gpg gets the passphrase. RPM 6 names the key with
            # _openpgp_sign_id, older versions with _gpg_name. RPM defaults to /usr/bin/gpg,
            # so __gpg is the gpg found on the PATH.
            for rpm in "$DIR"/rpm/*.rpm; do
                printf '%s' "$MAVEN_GPG_PASSPHRASE" | rpmsign --addsign --define "__gpg $(command -v gpg)" \
                    --define "_openpgp_sign_id $GPG_KEY" --define "_gpg_name $GPG_KEY" \
                    --define "_gpg_sign_cmd_extra_args --batch --pinentry-mode loopback --passphrase-fd 3" \
                    "$rpm" 3<&0 0< /dev/null
            done

            tar -C "$DIR/rpm" -cf - . | docker run --rm -i fedora:latest sh -c '
                mkdir /repo && tar -C /repo -xf - &&
                dnf -y -q install createrepo_c < /dev/null > /dev/null &&
                createrepo_c -q /repo > /dev/null &&
                tar -C /repo -cf - repodata' | tar -C "$DIR/rpm" -xf -
            sign --armor --detach-sign -o "$DIR/rpm/repodata/repomd.xml.asc" "$DIR/rpm/repodata/repomd.xml"

            sign --clearsign -o "$DIR/deb/InRelease" "$DIR/deb/Release"
            sign --armor --detach-sign -o "$DIR/deb/Release.gpg" "$DIR/deb/Release"

            gpg --armor --export "$GPG_KEY" > "$DIR/flowlogix.asc"
            '''
        }
    }

    writeFile file: "$dir/rpm/${name}.repo", text: """\
[flowlogix-$name]
name=$name by Flow Logix
baseurl=$url/rpm/$name
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=$url/flowlogix.asc
"""

    // Packages go up before the indexes that list them, so an index never points at a missing file.
    // repodata/ is replaced whole, since createrepo_c names its files after their checksums.
    sh """
    ssh $host mkdir -p $remote/deb/$name $remote/rpm/$name
    scp $dir/flowlogix.asc $host:$remote/
    scp $dir/deb/*.deb $host:$remote/deb/$name/
    scp $dir/deb/Packages $dir/deb/Packages.gz $dir/deb/Release $dir/deb/InRelease $dir/deb/Release.gpg \
        $host:$remote/deb/$name/
    scp $dir/rpm/*.rpm $dir/rpm/${name}.repo $host:$remote/rpm/$name/
    rsync -a --delete $dir/rpm/repodata/ $host:$remote/rpm/$name/repodata/
    """
}
