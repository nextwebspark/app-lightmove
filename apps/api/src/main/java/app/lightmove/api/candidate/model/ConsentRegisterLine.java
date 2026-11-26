package app.lightmove.api.candidate.model;

import java.util.UUID;

/** One line of the consent register: a consent a person was asked for, or their objection to being contacted. */
public sealed interface ConsentRegisterLine permits ConsentRegisterRow, ObjectionRegisterRow {

    UUID personId();

    String personName();
}
