import sun.nio.ch.IOUtil;

import java.io.*;
import java.net.Socket;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.*;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;

import javax.xml.bind.DatatypeConverter;


public class Client {

    final String LDAP_DOMAIN_NAME = "dc=nodomain,dc=com";
    final String LDAP_COMMON_NAME_SEARCH = "(&(objectclass=organizationRole)(cn=group.service1))";

    private PublicKey pubKey;
    private Key secretKey;
    private byte[] secretKeyAsymEncrypted;

    public static Object getPublicKey ( Attributes attrs ) {
        if (attrs == null) {
            return null;
        } else {
        /* Print each attribute */
            try {
                for (NamingEnumeration ae = attrs.getAll(); ae.hasMore(); ) {
                    Attribute attr = (Attribute)ae.next();
                    System.out.println( "attribute: " + attr.getID() );



        		/* print each value */
                    for (NamingEnumeration e = attr.getAll(); e.hasMore(); ) {
                        if ( attr.getID().equals( "description" ) )
                            return e.next();
                        e.next();
                    }
                }
            } catch (NamingException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static byte[] toByteArray(String s) {
        return DatatypeConverter.parseHexBinary(s);
    }

    private String getPublicKeyFromLDAP()
    {
        String ldapKey = null;
        LDAPConnector ldapConnector = new LDAPConnector();
        NamingEnumeration listName = null;
        try {
            listName = ldapConnector. search( LDAP_DOMAIN_NAME, LDAP_COMMON_NAME_SEARCH);

            while( listName.hasMore() )
            {
                SearchResult searchResult = (SearchResult) listName.next();
                ldapKey = getPublicKey(searchResult.getAttributes()).toString();
            }
            return ldapKey;
        } catch (NamingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void unwrapPublicKey()
    {
        String ldapKey = getPublicKeyFromLDAP();

        byte[] key = toByteArray(ldapKey);
        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec( key );

        try {

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.pubKey = keyFactory.generatePublic(pubKeySpec);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }

    private void generateSecretKeySymectrical()
    {
        KeyGenerator keyGenerator= null;

        try {
            keyGenerator =  KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        keyGenerator.init(256);
        this.secretKey = keyGenerator.generateKey();
    }

    private void wrapSecretSymetricalKeyInPubKey()
    {
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.WRAP_MODE, this.pubKey);
            this.secretKeyAsymEncrypted = cipher.wrap(this.secretKey);

        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
    }

    private byte[] decryptSymetrical(byte[] data)
    {
        Cipher cipher;

        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, this.secretKey);
            return cipher.doFinal(data);

        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void connectToService()
    {
        try {

            Socket socket = new Socket("localhost", 3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send secret key
            out.write(this.secretKeyAsymEncrypted);

            // Reveice message
            byte[] data;
            if((data = toByteArray(in.readUTF())).length>0)
            {
                System.out.println(new String(this.decryptSymetrical(data)));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String [] args)
    {
        Client client = new Client();
        client.unwrapPublicKey();
        client.generateSecretKeySymectrical();
        client.wrapSecretSymetricalKeyInPubKey();

        client.connectToService();


    }

}