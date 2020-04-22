/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package ir.batna.openvpn

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Base64
import android.widget.ProgressBar
import de.blinkt.openvpn.R
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.fragments.Utils
import de.blinkt.openvpn.views.FileSelectLayout
import java.io.*
import java.net.URLDecoder
import java.util.*

class OpenVPNConfig {

    private var mResult: VpnProfile? = null
    private var mPathsegments: List<String>? = null
    private var mImportTask: AsyncTask<Void, Void, Int>? = null
    private var mAliasName: String? = null
    private var mEmbeddedPwFile: String? = null
    private var fileSelectMap = HashMap<Utils.FileType, FileSelectLayout?>()

    fun doImportUri(data: Uri, contentResolver: ContentResolver, context: Context, onProfileImportListener: OnProfileImportListener) {

        var possibleName: String? = null
        if (data.scheme != null && data.scheme == "file" || data.lastPathSegment != null && (data.lastPathSegment!!.endsWith(".ovpn") || data.lastPathSegment!!.endsWith(".conf"))) {
            possibleName = data.lastPathSegment
            if (possibleName!!.lastIndexOf('/') != -1)
                possibleName = possibleName.substring(possibleName.lastIndexOf('/') + 1)

        }
        mPathsegments = data.pathSegments
        val cursor = contentResolver.query(data, null, null, null, null)
        try {

            if (cursor != null && cursor.moveToFirst()) {
                var columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                if (columnIndex != -1) {
                    val displayName = cursor.getString(columnIndex)
                    if (displayName != null)
                        possibleName = displayName
                }
                columnIndex = cursor.getColumnIndex("mime_type")
                if (columnIndex != -1) {
                }
            }
        } finally {
            cursor?.close()
        }
        if (possibleName != null) {
            possibleName = possibleName.replace(".ovpn", "")
            possibleName = possibleName.replace(".conf", "")
        }

        startImportTask(data, possibleName, "", context, contentResolver, onProfileImportListener)
    }

    private fun startImportTask(data: Uri, possibleName: String?, inlineData: String, context: Context, contentResolver: ContentResolver, onProfileImportListener: OnProfileImportListener) {
        mImportTask = object : AsyncTask<Void, Void, Int>() {
            private var mProgress: ProgressBar? = null

            override fun onPreExecute() {
            }

            override fun doInBackground(vararg params: Void): Int? {
                try {
                    var inputStream: InputStream?
                    if (data.scheme.equals("inline")) {
                        inputStream = inlineData.byteInputStream()
                    } else {
                        inputStream = contentResolver.openInputStream(data)
                    }

                    if (inputStream != null) {
                        doImport(inputStream)
                    }
                    if (mResult == null)
                        return -3
                } catch (se: IOException) {
                    return -2
                } catch (se: SecurityException) {
                    return -2
                }

                return 0
            }

            override fun onPostExecute(errorCode: Int?) {
                if (errorCode == 0) {
                    mResult!!.mName = getUniqueProfileName(possibleName, context)
                    saveProfile(context, onProfileImportListener)
                }
            }
        }.execute()
    }

    private fun doImport(inputStream: InputStream) {
        val cp = ConfigParser()
        try {
            val isr = InputStreamReader(inputStream)
            cp.parseConfig(isr)
            mResult = cp.convertProfile()
            embedFiles(cp)
            return

        } catch (e: IOException) {

        } catch (e: ConfigParser.ConfigParseError) {

        } finally {
            inputStream.close()
        }
        mResult = null
    }

    internal fun embedFiles(cp: ConfigParser?) {

        if (mResult!!.mPKCS12Filename != null) {
            val pkcs12file = findFileRaw(mResult!!.mPKCS12Filename)
            if (pkcs12file != null) {
                mAliasName = pkcs12file.name.replace(".p12", "")
            } else {
                mAliasName = "Imported PKCS12"
            }
        }
        mResult!!.mCaFilename = embedFile(mResult!!.mCaFilename, Utils.FileType.CA_CERTIFICATE, false)
        mResult!!.mClientCertFilename = embedFile(mResult!!.mClientCertFilename, Utils.FileType.CLIENT_CERTIFICATE, false)
        mResult!!.mClientKeyFilename = embedFile(mResult!!.mClientKeyFilename, Utils.FileType.KEYFILE, false)
        mResult!!.mTLSAuthFilename = embedFile(mResult!!.mTLSAuthFilename, Utils.FileType.TLS_AUTH_FILE, false)
        mResult!!.mPKCS12Filename = embedFile(mResult!!.mPKCS12Filename, Utils.FileType.PKCS12, false)
        mResult!!.mCrlFilename = embedFile(mResult!!.mCrlFilename, Utils.FileType.CRL_FILE, true)
        if (cp != null) {
            mEmbeddedPwFile = cp.authUserPassFile
            mEmbeddedPwFile = embedFile(cp.authUserPassFile, Utils.FileType.USERPW_FILE, false)
        }
    }

    private fun findFileRaw(filename: String?): File? {
        if (filename == null || filename == "")
            return null

        val sdcard = Environment.getExternalStorageDirectory()
        val root = File("/")
        val dirlist = HashSet<File>()
        for (i in mPathsegments!!.indices.reversed()) {
            var path = ""
            for (j in 0..i) {
                path += "/" + mPathsegments!![j]
            }
            if (path.indexOf(':') != -1 && path.lastIndexOf('/') > path.indexOf(':')) {
                var possibleDir = path.substring(path.indexOf(':') + 1, path.length)
                // Unquote chars in the  path
                try {
                    possibleDir = URLDecoder.decode(possibleDir, "UTF-8")
                } catch (ignored: UnsupportedEncodingException) {
                }
                possibleDir = possibleDir.substring(0, possibleDir.lastIndexOf('/'))
                dirlist.add(File(sdcard, possibleDir))
            }
            dirlist.add(File(path))
        }
        dirlist.add(sdcard)
        dirlist.add(root)


        val fileparts = filename.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (rootdir in dirlist) {
            var suffix = ""
            for (i in fileparts.indices.reversed()) {
                if (i == fileparts.size - 1)
                    suffix = fileparts[i]
                else
                    suffix = fileparts[i] + "/" + suffix

                val possibleFile = File(rootdir, suffix)
                if (possibleFile.canRead())
                    return possibleFile

            }
        }
        return null
    }

    private fun embedFile(filename: String?, type: Utils.FileType, onlyFindFileAndNullonNotFound: Boolean): String? {
        if (filename == null)
            return null

        if (VpnProfile.isEmbedded(filename))
            return filename

        val possibleFile = findFile(filename, type)
        return if (possibleFile == null)
            if (onlyFindFileAndNullonNotFound)
                null
            else
                filename
        else if (onlyFindFileAndNullonNotFound)
            possibleFile.absolutePath
        else
            readFileContent(possibleFile, type == Utils.FileType.PKCS12)
    }

    private fun findFile(filename: String?, fileType: Utils.FileType): File? {
        val foundfile = findFileRaw(filename)
        if (foundfile == null && filename != null && filename != "") {
        }
        fileSelectMap[fileType] = null
        return foundfile
    }

    internal fun readFileContent(possibleFile: File, base64encode: Boolean): String? {
        val filedata: ByteArray
        try {
            filedata = readBytesFromFile(possibleFile)
        } catch (e: IOException) {
            return null
        }
        val data: String
        if (base64encode) {
            data = Base64.encodeToString(filedata, Base64.DEFAULT)
        } else {
            data = String(filedata)
        }

        return VpnProfile.DISPLAYNAME_TAG + possibleFile.name + VpnProfile.INLINE_TAG + data
    }

    private fun readBytesFromFile(file: File): ByteArray {
        val input = FileInputStream(file)
        val len = file.length()
        if (len > VpnProfile.MAX_EMBED_FILE_SIZE)
            throw IOException("File size of file to import too large.")
        val bytes = ByteArray(len.toInt())
        var offset = 0
        var bytesRead: Int
        do {
            bytesRead = input.read(bytes, offset, bytes.size - offset)
            offset += bytesRead
        } while (offset < bytes.size && bytesRead >= 0)

        input.close()
        return bytes
    }

    private fun getUniqueProfileName(possibleName: String?, context: Context): String {

        var i = 0
        val vpl = ProfileManager.getInstance(context)
        var newname = possibleName
        if (mResult!!.mName != null && ConfigParser.CONVERTED_PROFILE != mResult!!.mName)
            newname = mResult!!.mName

        while (newname == null || vpl.getProfileByName(newname) != null) {
            i++
            if (i == 1)
                newname = context.getString(R.string.converted_profile)
            else
                newname = context.getString(R.string.converted_profile_i, i)
        }

        return newname
    }

    private fun saveProfile(context: Context, onProfileImportListener: OnProfileImportListener) {
        val vpl = ProfileManager.getInstance(context)

        if (!TextUtils.isEmpty(mEmbeddedPwFile))
            ConfigParser.useEmbbedUserAuth(mResult, mEmbeddedPwFile)
        vpl.addProfile(mResult)
        vpl.saveProfile(context, mResult)
        vpl.saveProfileList(context)
        onProfileImportListener.onProfileImported(mResult!!.uuid.toString())
    }
}