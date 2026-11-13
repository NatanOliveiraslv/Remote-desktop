package com.br.remote_agent.security;

import java.io.Console;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Handles secure persistence, encryption at rest, access restrictions, and resolution
 * of Agent credentials (computerId and agentToken).
 */
public final class SecureCredentialStore {

    private static final byte[] MAGIC_HEADER = new byte[]{'R', 'A', 'S', '1'}; // Remote Agent Secret v1
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int IV_LENGTH_BYTES = 12; // Standard 96-bit IV for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 65_536;

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String SECRET_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Resolves credentials using the following priority:
     * 1. Command-line flags (--computer-id and --token)
     * 2. System properties (agent.computer-id and agent.token)
     * 3. Environment variables (AGENT_COMPUTER_ID / AGENT_TOKEN)
     * 4. Encrypted credentials file
     * 5. Interactive console prompt (if console / stdin is accessible)
     */
    public AgentCredentials resolveCredentials(String[] args, Path credentialsPath) throws IOException {
        // 1. Command line arguments
        String argComputerId = null;
        String argToken = null;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--computer-id".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    argComputerId = args[++i];
                } else if (("--token".equalsIgnoreCase(args[i]) || "--agent-token".equalsIgnoreCase(args[i])) && i + 1 < args.length) {
                    argToken = args[++i];
                }
            }
        }
        if (argComputerId != null && argToken != null) {
            AgentCredentials creds = new AgentCredentials(UUID.fromString(argComputerId.trim()), argToken.trim());
            // Optionally persist the explicitly passed credentials
            saveCredentials(credentialsPath, creds);
            return creds;
        }

        // 2. System properties
        String propComputerId = System.getProperty("agent.computer-id");
        String propToken = System.getProperty("agent.token", System.getProperty("agent.agent-token"));
        if (propComputerId != null && propToken != null) {
            AgentCredentials creds = new AgentCredentials(UUID.fromString(propComputerId.trim()), propToken.trim());
            saveCredentials(credentialsPath, creds);
            return creds;
        }

        // 3. Environment variables
        String envComputerId = getEnv("AGENT_COMPUTER_ID", "REMOTE_AGENT_COMPUTER_ID");
        String envToken = getEnv("AGENT_TOKEN", "REMOTE_AGENT_TOKEN");
        if (envComputerId != null && envToken != null) {
            AgentCredentials creds = new AgentCredentials(UUID.fromString(envComputerId.trim()), envToken.trim());
            saveCredentials(credentialsPath, creds);
            return creds;
        }

        // 4. Encrypted file
        if (Files.exists(credentialsPath)) {
            try {
                return loadCredentials(credentialsPath);
            } catch (Exception e) {
                System.err.println("Failed to read encrypted credentials file (" + credentialsPath + "): " + e.getMessage());
                System.err.println("The file may be corrupted or created on a different host/user.");
            }
        }

        // 5. Interactive input fallback
        AgentCredentials prompted = promptUserForCredentials();
        if (prompted != null) {
            saveCredentials(credentialsPath, prompted);
            return prompted;
        }

        throw new IllegalStateException(
                "Agent credentials not found. Provide them via:\n"
                        + " - Encrypted file: " + credentialsPath.toAbsolutePath() + "\n"
                        + " - Environment variables: AGENT_COMPUTER_ID and AGENT_TOKEN\n"
                        + " - System properties: -Dagent.computer-id=<uuid> -Dagent.token=<token>\n"
                        + " - Command arguments: --computer-id <uuid> --token <token>"
        );
    }

    /**
     * Loads and decrypts credentials from the given path.
     */
    public AgentCredentials loadCredentials(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        byte[] fileBytes = Files.readAllBytes(path);

        if (fileBytes.length < MAGIC_HEADER.length + SALT_LENGTH_BYTES + IV_LENGTH_BYTES) {
            throw new IOException("Credentials file is corrupted or too short.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
        byte[] magic = new byte[MAGIC_HEADER.length];
        buffer.get(magic);

        if (!java.util.Arrays.equals(MAGIC_HEADER, magic)) {
            throw new IOException("Invalid credentials file header format.");
        }

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        buffer.get(salt);

        byte[] iv = new byte[IV_LENGTH_BYTES];
        buffer.get(iv);

        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        try {
            SecretKey key = deriveKey(salt);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decryptedBytes = cipher.doFinal(ciphertext);

            JsonNode jsonNode = objectMapper.readTree(decryptedBytes);
            UUID computerId = UUID.fromString(jsonNode.path("computerId").asText());
            String token = jsonNode.path("agentToken").asText();

            return new AgentCredentials(computerId, token);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to decrypt credentials: " + e.getMessage(), e);
        }
    }

    /**
     * Encrypts and writes credentials to the given path, enforcing strict file permissions.
     */
    public void saveCredentials(Path path, AgentCredentials credentials) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(credentials, "credentials must not be null");

        Path parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        secureRandom.nextBytes(iv);

        try {
            SecretKey key = deriveKey(salt);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            ObjectNode json = objectMapper.createObjectNode();
            json.put("computerId", credentials.computerId().toString());
            json.put("agentToken", credentials.agentToken());
            byte[] plaintext = objectMapper.writeValueAsBytes(json);

            byte[] ciphertext = cipher.doFinal(plaintext);

            ByteBuffer buffer = ByteBuffer.allocate(MAGIC_HEADER.length + salt.length + iv.length + ciphertext.length);
            buffer.put(MAGIC_HEADER);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(ciphertext);

            Files.write(path, buffer.array());
            restrictFilePermissions(path);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to encrypt credentials: " + e.getMessage(), e);
        }
    }

    /**
     * Derives a machine- and user-specific secret key using PBKDF2WithHmacSHA256.
     */
    private SecretKey deriveKey(byte[] salt) throws GeneralSecurityException {
        String machineId = resolveMachineFingerprint();
        PBEKeySpec spec = new PBEKeySpec(machineId.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Computes a machine/user fingerprint used to bind credentials to the current user and host.
     */
    private String resolveMachineFingerprint() {
        String userName = System.getProperty("user.name", "unknown_user");
        String userHome = System.getProperty("user.home", "");
        String osName = System.getProperty("os.name", "unknown_os");
        String computerName = System.getenv("COMPUTERNAME");
        if (computerName == null || computerName.isBlank()) {
            computerName = System.getenv("HOSTNAME");
        }
        if (computerName == null || computerName.isBlank()) {
            computerName = "localhost";
        }
        // Fixed salt pepper unique to Remote Agent
        return "RemoteAgentSecretSaltPepper:" + userName + ":" + userHome + ":" + osName + ":" + computerName;
    }

    /**
     * Restricts file permissions so only the file owner can read/write the file.
     */
    private void restrictFilePermissions(Path path) {
        try {
            // POSIX (Linux/macOS)
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> permissions = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE
                );
                Files.setPosixFilePermissions(path, permissions);
                return;
            }

            // Windows ACL
            if (path.getFileSystem().supportedFileAttributeViews().contains("acl")) {
                AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
                if (aclView != null) {
                    UserPrincipal owner = aclView.getOwner();
                    AclEntry ownerEntry = AclEntry.newBuilder()
                            .setType(AclEntryType.ALLOW)
                            .setPrincipal(owner)
                            .setPermissions(
                                    AclEntryPermission.READ_DATA,
                                    AclEntryPermission.WRITE_DATA,
                                    AclEntryPermission.APPEND_DATA,
                                    AclEntryPermission.READ_ATTRIBUTES,
                                    AclEntryPermission.WRITE_ATTRIBUTES,
                                    AclEntryPermission.READ_NAMED_ATTRS,
                                    AclEntryPermission.WRITE_NAMED_ATTRS,
                                    AclEntryPermission.READ_ACL,
                                    AclEntryPermission.SYNCHRONIZE
                            )
                            .build();
                    aclView.setAcl(Collections.singletonList(ownerEntry));
                }
            }
        } catch (Exception e) {
            // Not failing hard on permission setup, but logging warning
            System.err.println("Warning: Could not restrict credentials file permissions: " + e.getMessage());
        }
    }

    private AgentCredentials promptUserForCredentials() {
        Console console = System.console();
        if (console != null) {
            System.out.println("=== Remote Agent Initial Setup ===");
            System.out.println("Credentials not found. Please provide the details generated by the remote-server:");
            String idStr = console.readLine("Enter Computer ID (UUID): ");
            if (idStr == null || idStr.isBlank()) {
                return null;
            }
            char[] tokenChars = console.readPassword("Enter Agent Token: ");
            if (tokenChars == null || tokenChars.length == 0) {
                return null;
            }
            return new AgentCredentials(UUID.fromString(idStr.trim()), new String(tokenChars).trim());
        }

        // Fallback to Scanner if System.in has data
        try {
            if (System.in.available() > 0) {
                Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
                System.out.println("=== Remote Agent Setup ===");
                System.out.print("Enter Computer ID (UUID): ");
                if (!scanner.hasNextLine()) {
                    return null;
                }
                String idStr = scanner.nextLine().trim();
                System.out.print("Enter Agent Token: ");
                if (!scanner.hasNextLine()) {
                    return null;
                }
                String tokenStr = scanner.nextLine().trim();
                return new AgentCredentials(UUID.fromString(idStr), tokenStr);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static String getEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
