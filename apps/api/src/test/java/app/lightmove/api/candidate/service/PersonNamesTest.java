package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** When two spellings of a name may be matched as one person by a shared address. */
class PersonNamesTest {

    @Test
    @DisplayName("the same surname and a shared first initial agree, however the name was dressed")
    void spellingsOfOnePersonAgree() {
        assertThat(PersonNames.agree("Jon Smith", "Jonathan Smith")).isTrue();
        assertThat(PersonNames.agree("Smith, John", "John Smith")).isTrue();
        assertThat(PersonNames.agree("John Smith Jr.", "John Smith")).isTrue();
        assertThat(PersonNames.agree("John Smith, Jr.", "John Smith")).isTrue();
        assertThat(PersonNames.agree("Dr. Layla Haddad PhD", "Layla Haddad")).isTrue();
        assertThat(PersonNames.agree("José Álvarez", "Jose Alvarez")).isTrue();
        assertThat(PersonNames.agree("Omar Al Mansour", "Omar Al-Mansour")).isTrue();
        assertThat(PersonNames.agree("Hind Al Suwaidi", "Hind Suwaidi")).isTrue();
        assertThat(PersonNames.agree("Hind AlSuwaidi", "Hind Suwaidi")).isTrue();
    }

    @Test
    @DisplayName("two people at one address do not agree, even sharing a surname")
    void differentPeopleDoNotAgree() {
        assertThat(PersonNames.agree("Layla Haddad", "Omar Nasser")).isFalse();
        assertThat(PersonNames.agree("Layla Haddad", "Omar Haddad")).isFalse();
        assertThat(PersonNames.agree("Tom Delaney", "Tom Laney")).isFalse();
        assertThat(PersonNames.agree("Ann Davidson", "Ann Vidson")).isFalse();
    }

    @Test
    @DisplayName("a side with no name blocks nothing")
    void aMissingNameBlocksNothing() {
        assertThat(PersonNames.agree(null, "Layla Haddad")).isTrue();
        assertThat(PersonNames.agree("Layla Haddad", "   ")).isTrue();
        assertThat(PersonNames.agree("Haddad", "Layla Haddad")).isTrue();
    }

    @Test
    @DisplayName("a role inbox is recognised whatever its case or tag")
    void roleAddressesAreRecognised() {
        assertThat(RoleAddresses.isRoleAddress("info@acwa.example")).isTrue();
        assertThat(RoleAddresses.isRoleAddress("HR+jobs@acwa.example")).isTrue();
        assertThat(RoleAddresses.isRoleAddress("layla@acwa.example")).isFalse();
        assertThat(RoleAddresses.isRoleAddress("not-an-address")).isFalse();
    }
}
