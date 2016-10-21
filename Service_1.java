import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyStoreBuilderParameters;
import javax.xml.bind.DatatypeConverter;

public class Service_1 {

    final String LDAP_DOMAIN_NAME = "dc=nodomain,dc=com";

    private byte[] wrappedKEyFromClient;
    private Key symetricalKeyFromClient;
    private byte[] publicKeyForLDAP;
    private String message = "Hello friend";
    private byte[] encryptedMessageSymetrical;
    private KeyPair keyPair;

    public Service_1()
    {
        createKeyPair();
        generatePublicKey();
        uploadPrivateKeyToLDAP();
        startServerSocket();
    }

    public static byte[] toByteArray(String s) {
        return DatatypeConverter.parseHexBinary(s);
    }


    private void createKeyPair()
    {
        KeyPairGenerator keyPairGenerator = null;
        SecureRandom random = null;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            random = SecureRandom.getInstance("SHA1PRNG", "SUN");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        }

        keyPairGenerator.initialize(1024, random);
        this.keyPair = keyPairGenerator.generateKeyPair();
    }

    public static String toHexString(byte[] array) {return DatatypeConverter.printHexBinary(array);}


    public void generatePublicKey()
    {
        this.publicKeyForLDAP = this.keyPair.getPublic().getEncoded();
    }

    private void uploadPrivateKeyToLDAP()
    {
        LDAPConnector ldapConnector = new LDAPConnector();
        try {
            ldapConnector.updateAttribute("cn=group.service1,"+LDAP_DOMAIN_NAME, "description", toHexString(this.publicKeyForLDAP));
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }

    private byte[] encryptMessageSymetrical()
    {
        Cipher cipher;

        try {

            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, symetricalKeyFromClient);
            return cipher.doFinal(this.message.getBytes());

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void unwrapSymetricalKeyFromClient()
    {
        Cipher cipher;

        try {

            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.UNWRAP_MODE, this.keyPair.getPrivate());
            this.symetricalKeyFromClient = cipher.unwrap(this.wrappedKEyFromClient, "AES", Cipher.SECRET_KEY);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    private void startServerSocket()
    {
        try {
            ServerSocket serverSocket = new ServerSocket(3000);

            while(true)
            {
                Socket clientSocket = serverSocket.accept();
                System.out.println("A new client has connected to the server"+
                "\n"+"Starting transmission...");

                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());

                byte data[];
                if((data= toByteArray(in.readUTF())).length > 0)
                {
                    // Decrypt secret key
                    this.wrappedKEyFromClient = data;
                    unwrapSymetricalKeyFromClient();

                    // Encrypt and send message
                    encryptMessageSymetrical();
                    out.write(this.encryptedMessageSymetrical);
                }

                clientSocket.close();
                out.close();
                in.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String []args)
    {
        Service_1 service = new Service_1();
    }
}