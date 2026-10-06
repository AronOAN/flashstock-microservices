'use client';
import { useState,type FormEvent } from 'react';
export default function ContactClient(){const[status,setStatus]=useState('');function submit(event:FormEvent<HTMLFormElement>){event.preventDefault();setStatus('Formulario preparado. Conecta un endpoint Java de soporte antes de habilitar el envío real.');}
  return <section className="fs-section fs-narrow"><span className="fs-kicker">Contacto</span><h2>Conversemos</h2><p>Antonio Varas, Providencia · Santiago de Chile</p><form className="fs-form" onSubmit={submit}><label>Nombre<input className="fs-input" required/></label><label>Correo<input className="fs-input" type="email" required/></label><label>Mensaje<textarea className="fs-input" rows={6} required/></label><button className="fs-button" type="submit">Preparar mensaje</button></form>{status&&<p className="fs-status">{status}</p>}</section>;
}
