package com.crediflow.common.mybatis;

import com.crediflow.common.crypto.SensitiveDataCrypto;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.util.StringUtils;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SensitiveDataCryptoTypeHandler extends BaseTypeHandler<String> {

    private final SensitiveDataCrypto crypto;

    public SensitiveDataCryptoTypeHandler() {
        this.crypto = SensitiveDataCrypto.fromEnvOrNull();
        if (this.crypto == null) {
            throw new IllegalStateException("Missing environment variable for SensitiveDataCrypto: " + SensitiveDataCrypto.ENV_KEY);
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    private String encrypt(String raw) {
        if (!StringUtils.hasText(raw)) return raw;
        return crypto.encryptToBase64(raw);
    }

    private String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) return encrypted;
        return crypto.decryptFromBase64(encrypted);
    }
}
